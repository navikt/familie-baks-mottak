package no.nav.familie.ba.mottak.task

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.familie.ba.mottak.domene.NyBehandling
import no.nav.familie.ba.mottak.integrasjoner.SakService
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = SendTilSakTask.TASK_STEP_TYPE, beskrivelse = "Send til sak")
class SendTilSakTask(private val sakService: SakService) : AsyncTaskStep {

    override fun doTask(task: Task) {
        sakService.sendTilSak(jacksonObjectMapper().readValue(task.payload, NyBehandling::class.java))
    }

    override fun onCompletion(task: Task) {
        // Send melding til DittNav?
    }

    companion object {
        const val TASK_STEP_TYPE = "sendTilSak"
    }
}