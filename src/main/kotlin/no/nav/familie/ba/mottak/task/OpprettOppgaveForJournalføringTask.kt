package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.JournalpostService
import no.nav.familie.ba.mottak.integrasjoner.Journalstatus
import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = OpprettOppgaveForJournalføringTask.TASK_STEP_TYPE, beskrivelse = "Opprett journalføringsoppgave")
class OpprettOppgaveForJournalføringTask(private val journalpostService: JournalpostService,
                                         private val oppgaveClient: OppgaveClient,
                                         private val taskRepository: TaskRepository) : AsyncTaskStep {

    override fun doTask(task: Task) {
        val journalpost = journalpostService.hentJournalpost(task.payload)

        if (journalpost.journalstatus == Journalstatus.MOTTATT) {
            task.metadata["oppgaveId"] = "${oppgaveClient.opprettJournalføringsoppgave(journalpost).oppgaveId}"
            task.metadata["personIdent"] = journalpost.bruker?.id
            task.metadata["journalpostId"] = journalpost.journalpostId
            taskRepository.saveAndFlush(task)
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "opprettJournalføringsoppgave"
    }
}