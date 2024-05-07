package no.nav.familie.baks.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.domene.HendelseConsumer
import no.nav.familie.baks.mottak.domene.Hendelseslogg
import no.nav.familie.baks.mottak.domene.HendelsesloggRepository
import no.nav.familie.baks.mottak.integrasjoner.BrukerIdType.ORGNR
import no.nav.familie.baks.mottak.integrasjoner.Journalpost
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.Journalposttype
import no.nav.familie.baks.mottak.integrasjoner.Journalstatus
import no.nav.familie.baks.mottak.task.JournalhendelseKontantstøtteRutingTask
import no.nav.familie.baks.mottak.task.JournalhendelseBarnetrygdRutingTask
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.log.IdUtils
import no.nav.familie.log.mdc.MDCConstants
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.Properties

@Service
class JournalhendelseService(
    val journalpostClient: JournalpostClient,
    val taskService: TaskService,
    val hendelsesloggRepository: HendelsesloggRepository,
) {
    val barnetrygdKanalCounter = mutableMapOf<String, Counter>()
    val kontantstøtteKanalCounter = mutableMapOf<String, Counter>()
    val skannetOrdinærBarnetrygdSøknadCounter: Counter =
        Metrics.counter("barnetrygd.journalhendelse.kanal.skan.ny.ordinaer.soknad")
    val skannetUtvidetBarnetrygdSøknadCounter: Counter =
        Metrics.counter("barnetrygd.journalhendelse.kanal.skan.ny.utvidet.soknad")
    val skannetKontantstøtteSøknadCounter = Metrics.counter("kontantstotte.journalhendelse.kanal.skan.ny.soknad")
    val ignorerteCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.ignorerte")
    val feilCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.feilet")
    val logger: Logger = LoggerFactory.getLogger(JournalhendelseService::class.java)
    val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

    fun prosesserNyHendelse(
        consumerRecord: ConsumerRecord<Long, JournalfoeringHendelseRecord>,
        ack: Acknowledgment,
    ) {
        try {
            val hendelseRecord = consumerRecord.value()
            val callId = hendelseRecord.kanalReferanseId.toStringOrNull() ?: IdUtils.generateId()
            MDC.put(MDCConstants.MDC_CALL_ID, callId)

            if (erGyldigHendelsetype(hendelseRecord)) {
                if (hendelsesloggRepository.existsByHendelseIdAndConsumer(
                        hendelseRecord.hendelsesId.toString(),
                        CONSUMER_JOURNAL,
                    )
                ) {
                    ack.acknowledge()
                    return
                }

                secureLogger.info("Behandler gyldig journalhendelse: $hendelseRecord")

                behandleJournalhendelse(hendelseRecord)

                lagreHendelseslogg(consumerRecord, hendelseRecord, CONSUMER_JOURNAL)
            }

            ack.acknowledge()
        } catch (e: Exception) {
            logger.error("Feil ved lesing av journalhendelser ", e)
            feilCounter.count()
            throw e
        } finally {
            MDC.clear()
        }
    }

    private fun lagreHendelseslogg(
        consumerRecord: ConsumerRecord<Long, JournalfoeringHendelseRecord>,
        hendelseRecord: JournalfoeringHendelseRecord,
        hendelseConsumer: HendelseConsumer,
    ) {
        hendelsesloggRepository.save(
            Hendelseslogg(
                consumerRecord.offset(),
                hendelseRecord.hendelsesId.toString(),
                hendelseConsumer,
                mapOf(
                    "journalpostId" to hendelseRecord.journalpostId.toString(),
                    "hendelsesType" to hendelseRecord.hendelsesType.toString(),
                ).toProperties(),
            ),
        )
    }

    fun CharSequence.toStringOrNull(): String? {
        return if (this.isNotBlank()) this.toString() else null
    }

    private fun erGyldigHendelsetype(hendelseRecord: JournalfoeringHendelseRecord): Boolean {
        return GYLDIGE_HENDELSE_TYPER.contains(hendelseRecord.hendelsesType.toString()) &&
            (hendelseRecord.temaNytt != null && GYLDIGE_JOURNALPOST_TEMAER.contains(hendelseRecord.temaNytt.toString()))
    }

    fun behandleJournalhendelse(hendelseRecord: JournalfoeringHendelseRecord) {
        // hent journalpost fra saf
        val journalpostId = hendelseRecord.journalpostId.toString()
        val journalpost = journalpostClient.hentJournalpost(journalpostId)
        if (skalBehandleJournalpost(journalpost)) {
            when (journalpost.journalstatus) {
                Journalstatus.MOTTATT -> {
                    when {
                        "SKAN_" == journalpost.kanal?.substring(0, 5) -> {
                            behandleSkanningHendelser(journalpost)
                        }

                        "NAV_NO" == journalpost.kanal -> {
                            behandleNavnoHendelser(journalpost)
                        }

                        else -> {
                            logger.info("Ny journalhendelse med journalpostId=$journalpostId med status MOTTATT og kanal ${journalpost.kanal}")
                            incrementKanalCounter(journalpost)
                        }
                    }
                }

                else -> {
                    logger.debug("Ignorer journalhendelse hvor journalpost=$journalpostId har status ${journalpost.journalstatus}")
                    ignorerteCounter.count()
                }
            }
        }
    }

    private fun behandleNavnoHendelser(journalpost: Journalpost) {
        opprettJournalhendelseRutingTask(journalpost)
        incrementKanalCounter(journalpost)
    }

    private fun behandleSkanningHendelser(journalpost: Journalpost) {
        logger.info("Ny Journalhendelse med [journalpostId=${journalpost.journalpostId}, status=${journalpost.journalstatus}, tema=${journalpost.tema}, kanal=${journalpost.kanal}]")
        val erOrdinærBarnetrygdSøknad = journalpost.dokumenter?.find { it.brevkode == "NAV 33-00.07" } != null
        val erUtvidetBarnetrygdSøknad = journalpost.dokumenter?.find { it.brevkode == "NAV 33-00.09" } != null
        val erKontantstøtteSøknad = journalpost.dokumenter?.find { it.brevkode == "NAV 34-00.08" } != null

        opprettJournalhendelseRutingTask(journalpost)

        if (erOrdinærBarnetrygdSøknad) skannetOrdinærBarnetrygdSøknadCounter.increment()
        if (erUtvidetBarnetrygdSøknad) skannetUtvidetBarnetrygdSøknadCounter.increment()
        if (erKontantstøtteSøknad) skannetKontantstøtteSøknadCounter.increment()

        incrementKanalCounter(journalpost)
    }

    private fun incrementKanalCounter(
        journalpost: Journalpost,
    ) {
        val (søknadKanalCounter, counterName) = getKanalCounter(journalpost)
        val kanal = journalpost.kanal!!
        if (!søknadKanalCounter.containsKey(kanal)) {
            søknadKanalCounter[kanal] = Metrics.counter(counterName, "kanal", kanal)
        }
        søknadKanalCounter[kanal]!!.increment()
    }

    private fun getKanalCounter(journalpost: Journalpost) =
        if (journalpost.tema == Tema.BAR.name) {
            Pair(barnetrygdKanalCounter, "barnetrygd.journalhendelse.mottatt")
        } else {
            Pair(kontantstøtteKanalCounter, "kontantstotte.journalhendelse.mottatt")
        }

    private fun skalBehandleJournalpost(journalpost: Journalpost) =
        GYLDIGE_JOURNALPOST_TEMAER.contains(journalpost.tema) && journalpost.journalposttype == Journalposttype.I

    private fun opprettJournalhendelseRutingTask(journalpost: Journalpost) {
        val taskType =
            when (journalpost.tema) {
                Tema.BAR.name -> JournalhendelseBarnetrygdRutingTask.TASK_STEP_TYPE
                Tema.KON.name -> JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE
                else -> throw IllegalStateException("Ukjent tema ${journalpost.tema}")
            }
        Task(
            type = taskType,
            payload = journalpost.kanal!!,
            properties = opprettMetadata(journalpost),
        ).apply {
            taskService.save(this)
        }
    }

    private fun opprettMetadata(journalpost: Journalpost): Properties {
        return Properties().apply {
            if (journalpost.bruker != null && journalpost.bruker.type != ORGNR) {
                this["personIdent"] = journalpost.bruker.id
            }
            this["journalpostId"] = journalpost.journalpostId
            if (!MDC.get(MDCConstants.MDC_CALL_ID).isNullOrEmpty()) {
                this["callId"] = MDC.get(MDCConstants.MDC_CALL_ID) ?: IdUtils.generateId()
            }
        }
    }

    companion object {
        private val GYLDIGE_HENDELSE_TYPER = arrayOf("JournalpostMottatt", "TemaEndret")
        private val GYLDIGE_JOURNALPOST_TEMAER = listOf(Tema.BAR.name, Tema.KON.name)
        private val CONSUMER_JOURNAL = HendelseConsumer.JOURNAL_AIVEN
    }
}
