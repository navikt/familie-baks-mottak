package no.nav.familie.ba.mottak.task

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.familie.ba.mottak.config.FeatureToggleService
import no.nav.familie.ba.mottak.domene.NyBehandling
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = SendTilSakTask.TASK_STEP_TYPE, beskrivelse = "Send til sak")
class SendTilSakTask(private val sakClient: SakClient, private val featureToggleService: FeatureToggleService) : AsyncTaskStep {

    val logger: Logger = LoggerFactory.getLogger(SendTilSakTask::class.java)


    override fun doTask(task: Task) {
        if (featureToggleService.isEnabled("familie-ba-mottak.behandle-fodselshendelse")) {
            sakClient.sendTilSak(jacksonObjectMapper().readValue(task.payload, NyBehandling::class.java))
        } else {
            logger.info("Behandler ikke fødselshendelse, feature er skrudd av i Unleash")
        }

    }

    override fun onCompletion(task: Task) {
        // Send melding til DittNav?
    }

    companion object {
        const val TASK_STEP_TYPE = "sendTilSak"
    }
}