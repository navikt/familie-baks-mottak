package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.AktørClient
import no.nav.familie.ba.mottak.integrasjoner.Bruker
import no.nav.familie.ba.mottak.integrasjoner.BrukerIdType
import no.nav.familie.ba.mottak.integrasjoner.DokarkivClient
import no.nav.familie.ba.mottak.integrasjoner.JournalpostClient
import no.nav.familie.ba.mottak.integrasjoner.Journalstatus
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE,
                     beskrivelse = "Legger til Sak og ferdigstiller journalpost")
class OppdaterOgFerdigstillJournalpostTask(private val journalpostClient: JournalpostClient,
                                           private val dokarkivClient: DokarkivClient,
                                           private val sakClient: SakClient,
                                           private val aktørClient: AktørClient,
                                           private val taskRepository: TaskRepository) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(OppdaterOgFerdigstillJournalpostTask::class.java)

    override fun doTask(task: Task) {
        val journalpost = journalpostClient.hentJournalpost(task.payload)
                                  .takeUnless { it.bruker == null } ?: throw error("Journalpost ${task.payload} mangler bruker")

        when (journalpost.journalstatus) {
            Journalstatus.MOTTATT -> {
                val fagsakId = sakClient.hentSaksnummer(tilPersonIdent(journalpost.bruker!!))
                runCatching { // forsøk å journalføre automatisk
                    dokarkivClient.oppdaterJournalpostSak(journalpost, fagsakId)
                    dokarkivClient.ferdigstillJournalpost(journalpost.journalpostId)
                }.fold(
                        onSuccess = {
                            task.metadata["fagsakId"] = fagsakId
                            log.info("Har oppdatert og ferdigstilt journalpost ${journalpost.journalpostId}")
                            taskRepository.save(task)
                        },
                        onFailure = {
                            log.warn("Automatisk ferdigstilling feilet. Oppretter ny journalføringsoppgave for journalpost " +
                                     "${journalpost.journalpostId}.")
                            Task(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
                                        journalpost.journalpostId,
                                        task.metadata).also { taskRepository.save(it) }
                            return
                        }
                )

            }
            Journalstatus.JOURNALFOERT -> log.info("Skipper oppdatering og ferdigstilling av " +
                                                   "journalpost ${journalpost.journalpostId} som alt er ferdig journalført")
            else -> error("Uventet journalstatus ${journalpost.journalstatus} for journalpost ${journalpost.journalpostId}")
        }

        val nyTask = Task(
                type = OpprettBehandleSakOppgaveTask.TASK_STEP_TYPE,
                payload = task.payload,
                properties = task.metadata.apply {
                    this["fagsystem"] = "BA"
                }
        )
        taskRepository.save(nyTask)
    }

    private fun tilPersonIdent(bruker: Bruker): String {
        return when (bruker.type) {
            BrukerIdType.AKTOERID -> aktørClient.hentPersonident(bruker.id)
            else -> bruker.id
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "oppdaterOgFerdigstillJournalpost"
    }
}