package no.nav.familie.baks.mottak.task

import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService

abstract class AbstractJournalhendelseRutingTask(
    private val taskService: TaskService,
) : AsyncTaskStep {
    fun opprettJournalføringOppgaveTask(
        sakssystemMarkering: String,
        task: Task,
    ) {
        Task(
            type = OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
            payload = sakssystemMarkering,
            properties = task.metadata,
        ).apply { taskService.save(this) }
    }
}
