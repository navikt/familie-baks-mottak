package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.DokarkivClient
import no.nav.familie.ba.mottak.integrasjoner.JournalpostClient

import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository

import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE, beskrivelse = "Legger til Sak og ferdigstiller journalpost")
class OppdaterOgFerdigstillJournalpostTask(private val journalpostClient: JournalpostClient,
                                           private val dokarkivClient: DokarkivClient,
                                           private val taskRepository: TaskRepository) : AsyncTaskStep {

    override fun doTask(task: Task) {
        val journalpost = journalpostClient.hentJournalpost(task.payload)
        val fagsakId = "111111111" // TODO: Løses med favro-oppgaven "Lage fagsak fra Joark-hendelser". Kan vurdere om den heller skal følge med i payload'en til tasken

        dokarkivClient.oppdaterJournalpostSak(journalpost, fagsakId)
        dokarkivClient.ferdigstillJournalpost(journalpost.journalpostId)

        task.metadata["fagsakId"] = fagsakId
        task.metadata["personIdent"] = journalpost.bruker?.id
        task.metadata["journalpostId"] = journalpost.journalpostId
        taskRepository.saveAndFlush(task)
    }

    override fun onCompletion(task: Task) {
        val nyTask = Task.nyTask(
            type = OpprettBehandleSakOppgaveTask.TASK_STEP_TYPE,
            payload = task.payload,
            properties = task.metadata
        )
        taskRepository.save(nyTask)
    }

    companion object {
        const val TASK_STEP_TYPE = "oppdaterOgFerdigstillJournalpost"
    }
}