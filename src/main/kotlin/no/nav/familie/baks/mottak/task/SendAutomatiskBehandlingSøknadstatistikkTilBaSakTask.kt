package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.AutomatiskBehandlingSøknadStatistikkDto
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = SendAutomatiskBehandlingSøknadstatistikkTilBaSakTask.TASK_STEP_TYPE,
    beskrivelse = "Sender statistikk om automatisk behandling av søknader til ba-sak",
    maxAntallFeil = 3,
    settTilManuellOppfølgning = true,
)
class SendAutomatiskBehandlingSøknadstatistikkTilBaSakTask(
    private val baSakClient: BaSakClient,
) : AsyncTaskStep {
    override fun doTask(task: Task) {
        val statistikkDto =
            jsonMapper.readValue(
                task.payload,
                AutomatiskBehandlingSøknadStatistikkDto::class.java,
            )
        baSakClient.sendAutomatiskBehandlingSøknadstatistikk(statistikkDto)
    }

    companion object {
        const val TASK_STEP_TYPE = "sendAutomatiskBehandlingSøknadstatistikkTilBaSakTask"
    }
}
