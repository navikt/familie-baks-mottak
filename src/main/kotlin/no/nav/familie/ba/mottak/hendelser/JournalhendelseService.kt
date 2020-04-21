package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.config.FeatureToggleService
import no.nav.familie.ba.mottak.integrasjoner.Journalpost
import no.nav.familie.ba.mottak.integrasjoner.JournalpostClient
import no.nav.familie.ba.mottak.integrasjoner.Journalposttype
import no.nav.familie.ba.mottak.integrasjoner.Journalstatus
import no.nav.familie.ba.mottak.task.OppdaterOgFerdigstillJournalpostTask
import no.nav.familie.ba.mottak.task.OpprettJournalføringOppgaveTask
import no.nav.familie.log.IdUtils
import no.nav.familie.log.mdc.MDCConstants
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.util.*

@Service
class JournalhendelseService(val journalpostClient: JournalpostClient,
                             val taskRepository: TaskRepository,
                             val featureToggleService: FeatureToggleService) {

    val kanalNavnoCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.kanal.navno")
    val kanalSkannetsCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.kanal.skannets")
    val kanalAnnetCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.kanal.annet")
    val ignorerteCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.ignorerte")
    val logger: Logger = LoggerFactory.getLogger(JournalhendelseService::class.java)

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

    private fun behandleNavnoHendelser(journalpost: Journalpost) {
        if (featureToggleService.isEnabled("familie-ba-mottak.journalhendelse.behsak")) {
            val metadata = opprettMetadata(journalpost)
            val ferdigstillTask =
                    Task.nyTask(OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE,
                                journalpost.journalpostId,
                                metadata)
            taskRepository.save(ferdigstillTask)
            logger.info("Oppretter task OppdaterOgFerdigstillJournalpostTask, feature skrudd på")
        } else {
            logger.info("Behandler ikke journalhendelse, feature familie-ba-mottak.journalhendelse.behsak er skrudd av i Unleash")
        }

        kanalNavnoCounter.increment()
    }

    private fun behandleSkanningHendelser(journalpost: Journalpost) {
        logger.info("Ny Journalhendelse med journalpostId=${journalpost.journalpostId} med status MOTTATT og kanal SKAN_NETS")

        if (featureToggleService.isEnabled("familie-ba-mottak.journalhendelse.jfr")) {
            val metadata = opprettMetadata(journalpost)
            val journalføringsTask = Task.nyTask(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
                                                 journalpost.journalpostId,
                                                 metadata)
            taskRepository.save(journalføringsTask)
        } else {
            logger.info("Behandler ikke journalhendelse, feature familie-ba-mottak.journalhendelse.jfr er skrudd av i Unleash")
        }

        kanalSkannetsCounter.increment()
    }

    private fun skalBehandleJournalpost(journalpost: Journalpost) =
            journalpost.tema == "BAR" && journalpost.journalposttype == Journalposttype.I


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
}