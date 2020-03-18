package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.config.FeatureToggleService
import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.Hendelseslogg
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.integrasjoner.Journalpost
import no.nav.familie.ba.mottak.integrasjoner.JournalpostClient
import no.nav.familie.ba.mottak.integrasjoner.Journalposttype
import no.nav.familie.ba.mottak.integrasjoner.Journalstatus
import no.nav.familie.ba.mottak.task.OppdaterOgFerdigstillJournalpostTask
import no.nav.familie.ba.mottak.task.OpprettOppgaveForJournalføringTask
import no.nav.familie.log.IdUtils
import no.nav.familie.log.mdc.MDCConstants
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.*
import javax.transaction.Transactional


@Service
class JournalføringHendelseConsumer(val hendelsesloggRepository: HendelsesloggRepository,
                                    val journalpostClient: JournalpostClient,
                                    val taskRepository: TaskRepository,
                                    val featureToggleService: FeatureToggleService) {

    val kanalNavnoCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.kanal.navno")
    val kanalSkannetsCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.kanal.skannets")
    val kanalAnnetCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.kanal.annet")
    val feilCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.feilet")
    val ignorerteCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.ignorerte")

    val logger: Logger = LoggerFactory.getLogger(JournalføringHendelseConsumer::class.java)
    val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

    @KafkaListener(id = "familie-ba-mottak",
                   topics = ["\${JOURNALFOERINGHENDELSE_V1_TOPIC_URL}"],
                   containerFactory = "kafkaJournalføringHendelseListenerContainerFactory",
                   idIsGroup = false
    )
    @Transactional
    fun listen(consumerRecord: ConsumerRecord<Int, JournalfoeringHendelseRecord>, ack: Acknowledgment) {
        try {
            val hendelseRecord = consumerRecord.value()

            if (hendelsesloggRepository.existsByHendelseIdAndConsumer(hendelseRecord.hendelsesId.toString(), CONSUMER_JOURNAL)) {
                ack.acknowledge()
                return
            }

            if (erGyldigHendelsetype(hendelseRecord)) {
                val callId = hendelseRecord.kanalReferanseId.toStringOrNull() ?: IdUtils.generateId()
                MDC.put(MDCConstants.MDC_CALL_ID, callId)
                //hent journalpost fra saf
                val journalpostId = hendelseRecord.journalpostId.toString()
                val journalpost = journalpostClient.hentJournalpost(journalpostId)
                if (skalBehandleJournalpost(journalpost)) {
                    secureLogger.info("journalhendelse barnetrygd ${consumerRecord}")
                    when (journalpost.journalstatus) {
                        Journalstatus.MOTTATT -> {
                            when (journalpost.kanal) {
                                "SKAN_NETS" -> {
                                    logger.info("Ny Journalhendelse med journalpostId=$journalpostId med status MOTTATT og kanal SKAN_NETS")
                                    val metadata = opprettMetadata(journalpost, callId)
                                    if (featureToggleService.isEnabled("familie-ba-mottak.journalhendelse")) {
                                        val journalføringsTask = Task.nyTask(OpprettOppgaveForJournalføringTask.TASK_STEP_TYPE,
                                                                             journalpostId,
                                                                             metadata)
                                        taskRepository.save(journalføringsTask)
                                        logger.info("Skal opprette journalføringsTask")
                                    } else {
                                        logger.info("Behandler ikke journalhendelse, feature er skrudd av i Unleash")
                                    }

                                    kanalSkannetsCounter.increment()
                                }

                                "NAV_NO" -> {
                                    val metadata = opprettMetadata(journalpost, callId)

                                    if (featureToggleService.isEnabled("familie-ba-mottak.journalhendelse")) {
                                        val ferdigstillTask =
                                                Task.nyTask(OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE,
                                                            journalpostId,
                                                            metadata)
                                        taskRepository.save(ferdigstillTask)
                                        logger.info("Oppretter task OppdaterOgFerdigstillJournalpostTask, feature skrudd på")
                                    } else {
                                        logger.info("Behandler ikke journalhendelse, feature er skrudd av i Unleash")
                                    }


                                    kanalNavnoCounter.increment()
                                }

                                else -> {
                                    logger.info("Ny Journalhendelse med journalpostId=$journalpostId med status MOTTATT og kanal ${journalpost.kanal}")
                                    kanalAnnetCounter.count()
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

            hendelsesloggRepository.save(Hendelseslogg(consumerRecord.offset(),
                                                       hendelseRecord.hendelsesId.toString(),
                                                       CONSUMER_JOURNAL,
                                                       mapOf("journalpostId" to hendelseRecord.journalpostId.toString(),
                                                             "hendelsetype" to hendelseRecord.hendelsesType.toString()).toProperties()
            ))
            ack.acknowledge()
        } catch (e: Exception) {
            logger.error("Feil ved lesing av journalhendelser ", e)
            feilCounter.count()
            throw e
        } finally {
            MDC.clear()
        }
    }

    private fun skalBehandleJournalpost(journalpost: Journalpost) =
            journalpost.tema == "BAR" && journalpost.journalposttype == Journalposttype.I

    private fun opprettMetadata(journalpost: Journalpost,
                                callId: String?): Properties {
        return Properties().apply {
            if (journalpost.bruker != null) {
                this["personIdent"] = journalpost.bruker.id
            }
            this["journalpostId"] = journalpost.journalpostId
            this["callId"] = callId
        }
    }

    private fun erGyldigHendelsetype(hendelseRecord: JournalfoeringHendelseRecord): Boolean {
        return GYLDIGE_HENDELSE_TYPER.contains(hendelseRecord.hendelsesType.toString())
    }

    fun CharSequence.toStringOrNull(): String? {
        return if (!this.isBlank()) this.toString() else null
    }

    companion object {
        private val GYLDIGE_HENDELSE_TYPER = arrayOf("MidlertidigJournalført", "TemaEndret")
        private val CONSUMER_JOURNAL = HendelseConsumer.JOURNAL
    }
}