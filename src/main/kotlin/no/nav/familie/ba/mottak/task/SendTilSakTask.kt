package no.nav.familie.baks.mottak.task

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.familie.baks.mottak.domene.NyBehandling
import no.nav.familie.baks.mottak.integrasjoner.SakClient
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = SendTilSakTask.TASK_STEP_TYPE, beskrivelse = "Send til sak")
class SendTilSakTask(private val sakClient: SakClient) : AsyncTaskStep {

    val logger: Logger = LoggerFactory.getLogger(SendTilSakTask::class.java)

    override fun doTask(task: Task) {
        sakClient.sendTilSak(jacksonObjectMapper().readValue(task.payload, NyBehandling::class.java))
    }

    companion object {
        const val TASK_STEP_TYPE = "sendTilSak"
    }
}
