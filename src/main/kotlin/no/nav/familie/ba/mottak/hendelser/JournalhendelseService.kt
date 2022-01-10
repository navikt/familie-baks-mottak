package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.Hendelseslogg
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.integrasjoner.BrukerIdType.ORGNR
import no.nav.familie.ba.mottak.integrasjoner.Journalpost
import no.nav.familie.ba.mottak.integrasjoner.JournalpostClient
import no.nav.familie.ba.mottak.integrasjoner.Journalposttype
import no.nav.familie.ba.mottak.integrasjoner.Journalstatus
import no.nav.familie.ba.mottak.task.JournalhendelseRutingTask
import no.nav.familie.log.IdUtils
import no.nav.familie.log.mdc.MDCConstants
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.*

@Service
class JournalhendelseService(
    val journalpostClient: JournalpostClient,
    val taskRepository: TaskRepository,
    val hendelsesloggRepository: HendelsesloggRepository,
) {

    val kanalCounter = mutableMapOf<String, Counter>()
    val skannetOrdinæreSøknaderCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.kanal.skan.ny.ordinaer.soknad")
    val skannetUtvidedeSøknaderCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.kanal.skan.ny.utvidet.soknad")
    val ignorerteCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.ignorerte")
    val feilCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.feilet")
    val logger: Logger = LoggerFactory.getLogger(JournalhendelseService::class.java)
    val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

    fun prosesserNyHendelse(
        consumerRecord: ConsumerRecord<Long, JournalfoeringHendelseRecord>,
        ack: Acknowledgment
    ) {
        try {
            val hendelseRecord = consumerRecord.value()
            val callId = hendelseRecord.kanalReferanseId.toStringOrNull() ?: IdUtils.generateId()
            MDC.put(MDCConstants.MDC_CALL_ID, callId)

            if (erGyldigHendelsetype(hendelseRecord)) {
                if (hendelsesloggRepository.existsByHendelseIdAndConsumer(hendelseRecord.hendelsesId.toString(), CONSUMER_JOURNAL)) {
                    ack.acknowledge()
                    return
                }

                secureLogger.info("Mottatt gyldig hendelse: $hendelseRecord")
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
        hendelseConsumer: HendelseConsumer
    ) {
        hendelsesloggRepository.save(
            Hendelseslogg(
                consumerRecord.offset(),
                hendelseRecord.hendelsesId.toString(),
                hendelseConsumer,
                mapOf(
                    "journalpostId" to hendelseRecord.journalpostId.toString(),
                    "hendelsesType" to hendelseRecord.hendelsesType.toString()
                ).toProperties()
            )
        )
    }

    fun CharSequence.toStringOrNull(): String? {
        return if (this.isNotBlank()) this.toString() else null
    }

    fun bareLagreLoggOgAckAiven(consumerRecord: ConsumerRecord<Long, JournalfoeringHendelseRecord>,
                           ack: Acknowledgment) {
        val hendelseRecord = consumerRecord.value()
        if (erGyldigHendelsetype(hendelseRecord)) {
            if (!hendelsesloggRepository.existsByHendelseIdAndConsumer(
                    hendelseRecord.hendelsesId.toString(),
                    HendelseConsumer.JOURNAL_AIVEN
                )
            ) {
                lagreHendelseslogg(consumerRecord, hendelseRecord, HendelseConsumer.JOURNAL_AIVEN)
            }
        }
        ack.acknowledge()
    }

    private fun erGyldigHendelsetype(hendelseRecord: JournalfoeringHendelseRecord): Boolean {
        return GYLDIGE_HENDELSE_TYPER.contains(hendelseRecord.hendelsesType.toString())
                && (hendelseRecord.temaNytt != null && hendelseRecord.temaNytt.toString() == "BAR")
    }

    fun behandleJournalhendelse(hendelseRecord: JournalfoeringHendelseRecord) {
        //hent journalpost fra saf
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
                            incrementKanalCounter(journalpost.kanal.toString())
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
        incrementKanalCounter(journalpost.kanal!!)
    }

    private fun behandleSkanningHendelser(journalpost: Journalpost) {
        logger.info("Ny Journalhendelse med [journalpostId=${journalpost.journalpostId}, status=${journalpost.journalstatus}, tema=${journalpost.tema}, kanal=${journalpost.kanal}]")
        val erOrdinærSønad = journalpost.dokumenter?.find { it.brevkode == "NAV 33-00.07" } != null
        val erUtvidetSøknad = journalpost.dokumenter?.find { it.brevkode == "NAV 33-00.09" } != null

        opprettJournalhendelseRutingTask(journalpost)

        if (erOrdinærSønad) skannetOrdinæreSøknaderCounter.increment()
        if (erUtvidetSøknad) skannetUtvidedeSøknaderCounter.increment()
        incrementKanalCounter(journalpost.kanal!!)
    }

    private fun incrementKanalCounter(kanal: String) {
        if (!kanalCounter.containsKey(kanal)) {
            kanalCounter[kanal] = Metrics.counter("barnetrygd.journalhendelse.mottatt", "kanal", kanal)
        }
        kanalCounter[kanal]!!.increment()
    }

    private fun skalBehandleJournalpost(journalpost: Journalpost) =
        journalpost.tema == "BAR" && journalpost.journalposttype == Journalposttype.I


    private fun opprettJournalhendelseRutingTask(journalpost: Journalpost) {
        Task.nyTask(
            JournalhendelseRutingTask.TASK_STEP_TYPE,
            journalpost.kanal!!,
            opprettMetadata(journalpost)
        ).apply { taskRepository.save(this) }
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

        private val GYLDIGE_HENDELSE_TYPER = arrayOf("MidlertidigJournalført", "JournalpostMottatt", "TemaEndret") // MidlertidigJournalført -> JournalpostMottatt på aiven TODO MidlertidigJournalført kan fjernes når vi er over på aiven
        private val CONSUMER_JOURNAL = HendelseConsumer.JOURNAL_AIVEN
    }
}
