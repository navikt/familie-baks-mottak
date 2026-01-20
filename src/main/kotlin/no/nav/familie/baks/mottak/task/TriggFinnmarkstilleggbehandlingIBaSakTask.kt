package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE,
    beskrivelse = "Trigger finnmarkstilleggbehandling i ba-sak",
    maxAntallFeil = 3,
    settTilManuellOppfølgning = true,
)
class TriggFinnmarkstilleggbehandlingIBaSakTask(
    private val baSakClient: BaSakClient,
) : AsyncTaskStep {
    override fun doTask(task: Task) {
        baSakClient.sendFinnmarkstilleggTilBaSak(task.payload)
    }

    companion object {
        const val TASK_STEP_TYPE = "triggFinnmarkstilleggbehandlingIBaSakTask"
    }
}
