package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.OppholdAnnetSted
import no.nav.familie.kontrakter.felles.personopplysning.Oppholdsadresse
import no.nav.familie.kontrakter.felles.svalbard.erKommunePåSvalbard
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = SvalbardtilleggTask.TASK_STEP_TYPE,
    beskrivelse = "Sjekker om en adressehendelse skal trigge Svalbardtillegg autovedtak av Svalbardtillegg",
    maxAntallFeil = 3,
    triggerTidVedFeilISekunder = 60,
)
class SvalbardtilleggTask(
    private val pdlClient: PdlClient,
    private val baSakClient: BaSakClient,
    private val unleashNextMedContextService: UnleashNextMedContextService,
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

        val erIkkeSøkerOgHarIkkeLøpendeBarnetrygd = baSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(ident).isEmpty()
        if (erIkkeSøkerOgHarIkkeLøpendeBarnetrygd) {
            secureLogger.info("Fant ingen fagsaker der ident $ident er søker eller har løpende barnetrygd, hopper ut av SvalbardtilleggTask.")
            task.metadata["resultat"] = "INGEN_LØPENDE_BARNETRYGD"
            return
        }

        secureLogger.info("Person med ident $ident har flyttet til eller fra Svalbard.")
        if (unleashNextMedContextService.isEnabled(FeatureToggleConfig.SEND_OPPHOLDSADRESSE_HENDELSER_TIL_BA_SAK)) {
            baSakClient.sendSvalbardtilleggTilBaSak(ident)
        }
        task.metadata["resultat"] = "HAR_FLYTTET_TIL_ELLER_FRA_SVALBARD"
    }

    companion object {
        const val TASK_STEP_TYPE = "svalbardtilleggTask"
        private val secureLogger = LoggerFactory.getLogger("secureLogger")
    }
}

private fun Oppholdsadresse.erPåSvalbard(): Boolean =
    oppholdAnnetSted in setOf(OppholdAnnetSted.PAA_SVALBARD.kode, OppholdAnnetSted.PAA_SVALBARD.name) ||
        (vegadresse?.kommunenummer?.let { erKommunePåSvalbard(it) })
            ?: (matrikkeladresse?.kommunenummer?.let { erKommunePåSvalbard(it) })
            ?: false
