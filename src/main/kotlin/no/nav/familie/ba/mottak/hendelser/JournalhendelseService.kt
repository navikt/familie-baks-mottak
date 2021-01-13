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
class JournalhendelseService(val journalpostClient: JournalpostClient,
                             val taskRepository: TaskRepository,
                             val hendelsesloggRepository: HendelsesloggRepository,
                             val featureToggleService: FeatureToggleService) {

    val kanalNavnoCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.kanal.navno")
    val kanalSkannetsCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.kanal.skannets")
    val kanalAnnetCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.kanal.annet")
    val ignorerteCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.ignorerte")
    val feilCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.feilet")
    val logger: Logger = LoggerFactory.getLogger(JournalhendelseService::class.java)
    val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

    fun prosesserNyHendelse(consumerRecord: ConsumerRecord<Long, JournalfoeringHendelseRecord>,
                                    ack: Acknowledgment) {
        try {
            val hendelseRecord = consumerRecord.value()
            val callId = hendelseRecord.kanalReferanseId.toStringOrNull() ?: IdUtils.generateId()
            MDC.put(MDCConstants.MDC_CALL_ID, callId)

            if (hendelsesloggRepository.existsByHendelseIdAndConsumer(hendelseRecord.hendelsesId.toString(),
                                                                      CONSUMER_JOURNAL)) {
                ack.acknowledge()
                return
            }

            if (erGyldigHendelsetype(hendelseRecord)) {
                secureLogger.info("Mottatt gyldig hendelse: $hendelseRecord")
                behandleJournalhendelse(hendelseRecord)
            }

            hendelsesloggRepository.save(Hendelseslogg(consumerRecord.offset(),
                                                       hendelseRecord.hendelsesId.toString(),
                                                       CONSUMER_JOURNAL,
                                                       mapOf("journalpostId" to hendelseRecord.journalpostId.toString(),
                                                             "hendelsesType" to hendelseRecord.hendelsesType.toString()).toProperties()
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

    fun CharSequence.toStringOrNull(): String? {
        return if (this.isNotBlank()) this.toString() else null
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

    private fun behandleNavnoHendelser(journalpost: Journalpost) {
        if (featureToggleService.isEnabled("familie-ba-mottak.journalhendelse.behsak", true)) {
            opprettJournalhendelseRutingTask(journalpost)
            logger.info("Oppretter JournalhendelseRutingTask for \"NAV_NO\"-hendelse, feature skrudd på")
        } else {
            logger.info("Behandler ikke journalhendelse, feature familie-ba-mottak.journalhendelse.behsak er skrudd av i Unleash")
        }

        kanalNavnoCounter.increment()
    }

    private fun behandleSkanningHendelser(journalpost: Journalpost) {
        logger.info("Ny Journalhendelse med [journalpostId=${journalpost.journalpostId}, status=${journalpost.journalstatus}, tema=${journalpost.tema}, kanal=${journalpost.kanal}]")

        if (featureToggleService.isEnabled("familie-ba-mottak.journalhendelse.jfr")) {
            opprettJournalhendelseRutingTask(journalpost)
        } else {
            logger.info("Behandler ikke journalhendelse, feature familie-ba-mottak.journalhendelse.jfr er skrudd av i Unleash")
        }

        kanalSkannetsCounter.increment()
    }

    private fun skalBehandleJournalpost(journalpost: Journalpost) =
            journalpost.tema == "BAR" && journalpost.journalposttype == Journalposttype.I


    private fun opprettJournalhendelseRutingTask(journalpost: Journalpost) {
        Task.nyTask(JournalhendelseRutingTask.TASK_STEP_TYPE,
                    journalpost.kanal!!,
                    opprettMetadata(journalpost)).apply { taskRepository.save(this) }
    }

    private fun opprettMetadata(journalpost: Journalpost): Properties {
        return Properties().apply {
            if (journalpost.bruker != null) {
                this["personIdent"] = journalpost.bruker.id
            }
            this["journalpostId"] = journalpost.journalpostId
            if (!MDC.get(MDCConstants.MDC_CALL_ID).isNullOrEmpty()) {
                this["callId"] = MDC.get(MDCConstants.MDC_CALL_ID) ?: IdUtils.generateId()
            }
        }
    }

    companion object {
        private val GYLDIGE_HENDELSE_TYPER = arrayOf("MidlertidigJournalført", "TemaEndret")
        private val CONSUMER_JOURNAL = HendelseConsumer.JOURNAL
    }
}
