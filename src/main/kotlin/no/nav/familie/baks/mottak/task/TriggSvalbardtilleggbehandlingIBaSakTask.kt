package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig.Companion.SEND_OPPHOLDSADRESSE_HENDELSER_TIL_BA_SAK
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleService
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.error.RekjørSenereException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@TaskStepBeskrivelse(
    taskStepType = TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE,
    beskrivelse = "Trigger svalbardtilleggbehandling i ba-sak",
    maxAntallFeil = 3,
    settTilManuellOppfølgning = true,
)
class TriggSvalbardtilleggbehandlingIBaSakTask(
    private val baSakClient: BaSakClient,
    private val featureToggleService: FeatureToggleService,
) : AsyncTaskStep {
    override fun doTask(task: Task) {
        val ident = task.payload

        if (featureToggleService.isEnabled(SEND_OPPHOLDSADRESSE_HENDELSER_TIL_BA_SAK)) {
            baSakClient.sendSvalbardtilleggTilBaSak(ident)
        } else {
            throw RekjørSenereException(
                årsak = "Toggle er skrudd av, prøver igjen om 1 uke",
                triggerTid = LocalDateTime.now().plusWeeks(1),
            )
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "TriggSvalbardtilleggbehandlingIBaSakTask"
    }
}
