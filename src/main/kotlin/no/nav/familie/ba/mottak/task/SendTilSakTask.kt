package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.SakService
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = SendTilSakTask.TASK_STEP_TYPE, beskrivelse = "Send til sak")
class SendTilSakTask(private val taskRepository: TaskRepository, private val sakService: SakService) : AsyncTaskStep {

    override fun doTask(task: Task) {
        println("SendTilSakTask")
        sakService.sendTilSak(task.payload)
    }

    override fun onCompletion(task: Task) {
        // Send melding til DittNav?
    }

    companion object {
        const val TASK_STEP_TYPE = "sendTilSak"
    }
}