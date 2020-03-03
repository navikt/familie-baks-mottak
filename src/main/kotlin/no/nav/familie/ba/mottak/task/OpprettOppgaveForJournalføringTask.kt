package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = OpprettOppgaveForJournalføringTask.TASK_STEP_TYPE, beskrivelse = "Opprett journalføringsoppgave")
class OpprettOppgaveForJournalføringTask(private val oppgaveClient: OppgaveClient) : AsyncTaskStep {

    override fun doTask(task: Task) {
        oppgaveClient.opprettJournalføringsoppgave(task.payload)
    }

    companion object {
        const val TASK_STEP_TYPE = "opprettJournalføringsoppgave"
    }
}