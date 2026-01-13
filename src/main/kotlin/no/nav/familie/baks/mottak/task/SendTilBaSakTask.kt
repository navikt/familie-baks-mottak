package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.domene.NyBehandling
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.restklient.config.jsonMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = SendTilBaSakTask.TASK_STEP_TYPE, beskrivelse = "Send til sak")
class SendTilBaSakTask(
    private val baSakClient: BaSakClient,
) : AsyncTaskStep {
    val logger: Logger = LoggerFactory.getLogger(SendTilBaSakTask::class.java)

    override fun doTask(task: Task) {
        baSakClient.sendTilSak(jsonMapper.readValue(task.payload, NyBehandling::class.java))
    }

    companion object {
        const val TASK_STEP_TYPE = "sendTilBaSak"
    }
}
