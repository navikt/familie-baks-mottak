package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE,
    beskrivelse = "Trigger svalbardtilleggbehandling i ba-sak",
    maxAntallFeil = 3,
    settTilManuellOppfølgning = true,
)
class TriggSvalbardtilleggbehandlingIBaSakTask(
    private val baSakClient: BaSakClient,
) : AsyncTaskStep {
    override fun doTask(task: Task) {
        baSakClient.sendSvalbardtilleggTilBaSak(task.payload)
    }

    companion object {
        const val TASK_STEP_TYPE = "triggSvalbardtilleggbehandlingIBaSakTask"
    }
}
