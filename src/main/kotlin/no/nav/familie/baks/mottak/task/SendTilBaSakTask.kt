package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.domene.NyBehandling
import no.nav.familie.baks.mottak.integrasjoner.BaSakClientService
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = SendTilBaSakTask.TASK_STEP_TYPE, beskrivelse = "Send til sak")
class SendTilBaSakTask(
    private val baSakClientService: BaSakClientService,
) : AsyncTaskStep {
    val logger: Logger = LoggerFactory.getLogger(SendTilBaSakTask::class.java)

    override fun doTask(task: Task) {
        baSakClientService.sendTilSak(jsonMapper.readValue(task.payload, NyBehandling::class.java))
    }

    companion object {
        const val TASK_STEP_TYPE = "sendTilBaSak"
    }
}
