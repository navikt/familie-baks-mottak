package no.nav.familie.ba.mottak.task

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.familie.ba.mottak.config.FeatureToggleService
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.felles.PersonIdent
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Properties

@Service
@TaskStepBeskrivelse(
    taskStepType = SendIdenthendelseTilSakTask.TASK_STEP_TYPE,
    beskrivelse = "Send identhendelse til sak"
)
class SendIdenthendelseTilSakTask(private val sakClient: SakClient, private val featureToggleService: FeatureToggleService) :
    AsyncTaskStep {

    val logger: Logger = LoggerFactory.getLogger(SendIdenthendelseTilSakTask::class.java)

    override fun doTask(task: Task) {
        sakClient.sendIdenthendelseTilSak(jacksonObjectMapper().readValue(task.payload, PersonIdent::class.java))
    }

    companion object {

        const val TASK_STEP_TYPE = "sendIdenthendelseTilSak"

        fun opprettTask(ident: PersonIdent): Task {
            return Task.nyTask(
                type = TASK_STEP_TYPE,
                payload = objectMapper.writeValueAsString(ident),
                properties = Properties().apply {
                    this["personIdent"] = ident.ident
                }
            )
        }
    }
}
