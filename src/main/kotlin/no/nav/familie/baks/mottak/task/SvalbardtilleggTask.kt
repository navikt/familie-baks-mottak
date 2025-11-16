package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.OppholdAnnetSted
import no.nav.familie.kontrakter.felles.personopplysning.Oppholdsadresse
import no.nav.familie.kontrakter.felles.svalbard.erKommunePåSvalbard
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Status.KLAR_TIL_PLUKK
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.Properties
import kotlin.random.Random.Default.nextLong

@Service
@TaskStepBeskrivelse(
    taskStepType = SvalbardtilleggTask.TASK_STEP_TYPE,
    beskrivelse = "Sjekker om en adressehendelse skal trigge svalbardtilleggbehandling i ba-sak",
    maxAntallFeil = 3,
    triggerTidVedFeilISekunder = 60,
)
class SvalbardtilleggTask(
    private val pdlClient: PdlClient,
    private val baSakClient: BaSakClient,
    private val taskService: TaskService,
    private val environment: Environment,
) : AsyncTaskStep {
    override fun doTask(task: Task) {
        val ident = task.payload

        val oppholdsadresser = pdlClient.hentPerson(ident, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse
        val ingenOppholdsadressePåSvalbard = oppholdsadresser.none { it.erPåSvalbard() }
        if (ingenOppholdsadressePåSvalbard) {
            secureLogger.info("Det finnes ingen oppholdsadresse på Svalbard for ident $ident, hopper ut av SvalbardtilleggTask.")
            task.metadata["resultat"] = "INGEN_OPPHOLDSADRESSE_PÅ_SVALBARD"
            return
        }

        val eksistererUkjørtTaskForIdent =
            taskService
                .finnTaskMedPayloadOgType(ident, TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE)
                ?.status in setOf(Status.UBEHANDLET, KLAR_TIL_PLUKK)

        if (eksistererUkjørtTaskForIdent) {
            secureLogger.info("Det finnes allerede en ukjørt TriggSvalbardtilleggbehandlingIBaSakTask for ident $ident, hopper ut av SvalbardtilleggTask")
            task.metadata["resultat"] = "EKSISTERER_UKJØRT_TASK"
            return
        }

        val erIkkeSøkerOgHarIkkeLøpendeBarnetrygd = baSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(ident).isEmpty()
        if (erIkkeSøkerOgHarIkkeLøpendeBarnetrygd) {
            secureLogger.info("Fant ingen fagsaker der ident $ident er søker eller har løpende barnetrygd, hopper ut av SvalbardtilleggTask.")
            task.metadata["resultat"] = "INGEN_LØPENDE_BARNETRYGD"
            return
        }

        secureLogger.info("Person med ident $ident har oppholdsadresse Svalbard, trigger svalbardtilleggbehandling i ba-sak.")
        task.metadata["resultat"] = "HAR_FLYTTET_TIL_ELLER_FRA_SVALBARD"
        taskService.save(
            Task(
                type = TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE,
                payload = ident,
                properties =
                    Properties().apply {
                        this["ident"] = ident
                    },
            ).medTriggerTid(finnTriggertidForÅSendeIdentTilBaSak()),
        )
    }

    private fun finnTriggertidForÅSendeIdentTilBaSak(): LocalDateTime =
        LocalDateTime.now().run {
            if (environment.activeProfiles.contains("prod")) {
                // Legger på tilfeldig delay på inntil 3 minutter for å
                // unngå at flere kall gjøres samtidig til ba-sak
                plusHours(1).plusSeconds(nextLong(0, 180)).coerceAtLeast(tidligsteTriggerTidForÅSendeSvalbardtilleggTilBaSak)
            } else {
                this
            }
        }

    companion object {
        const val TASK_STEP_TYPE = "svalbardtilleggTask"
        val tidligsteTriggerTidForÅSendeSvalbardtilleggTilBaSak = LocalDateTime.of(2025, 12, 1, 0, 0)
        private val secureLogger = LoggerFactory.getLogger("secureLogger")
    }
}

private fun Oppholdsadresse.erPåSvalbard(): Boolean =
    oppholdAnnetSted in setOf(OppholdAnnetSted.PAA_SVALBARD.kode, OppholdAnnetSted.PAA_SVALBARD.name) ||
        (vegadresse?.kommunenummer?.let { erKommunePåSvalbard(it) })
            ?: (matrikkeladresse?.kommunenummer?.let { erKommunePåSvalbard(it) })
            ?: false
