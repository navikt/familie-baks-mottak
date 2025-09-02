package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.ba.finnmarkstillegg.kommuneErIFinnmarkEllerNordTroms
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
@TaskStepBeskrivelse(
    taskStepType = FinnmarkstilleggTask.TASK_STEP_TYPE,
    beskrivelse = "Sjekker om en adressehendelse skal trigge Finnmarkstillegg autovedtak av Finnmarkstillegg",
    maxAntallFeil = 3,
    triggerTidVedFeilISekunder = 60,
)
class FinnmarkstilleggTask(
    private val pdlClient: PdlClient,
    private val baSakClient: BaSakClient,
    private val unleashNextMedContextService: UnleashNextMedContextService,
) : AsyncTaskStep {
    override fun doTask(task: Task) {
        val payload =
            try {
                objectMapper.readValue(task.payload, VurderFinnmarkstillleggTaskDTO::class.java)
            } catch (_: Exception) {
                return
            }

        val ident = payload.ident
        val bostedskommune = payload.bostedskommune
        val bostedskommuneFomDato = payload.bostedskommuneFomDato

        if (bostedskommune == null || bostedskommuneFomDato == null) {
            secureLogger.info("Bostedskommune eller fom-dato er ikke satt for ident $ident, hopper ut av FinnmarkstilleggTask")
            task.metadata["resultat"] = "BOSTEDSKOMMUNE_ELLER_FOM_DATO_IKKE_SATT"
            return
        }

        val adresser = pdlClient.hentPerson(ident, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse.filterNotNull()
        if (adresser.isEmpty()) {
            secureLogger.info("Fant ingen bostedsadresser for ident $ident, hopper ut av FinnmarkstilleggTask")
            task.metadata["resultat"] = "INGEN_BOSTEDSADRESSE"
            return
        }

        val sisteBostedsadresseFørHendelse =
            adresser
                .sortedBy { it.gyldigFraOgMed }
                .lastOrNull { it.gyldigFraOgMed != null && it.gyldigFraOgMed!!.isBefore(bostedskommuneFomDato) }

        val forrigeBostedskommuneErIFinnmarkEllerNordTroms = sisteBostedsadresseFørHendelse?.erIFinnmarkEllerNordTroms() ?: false
        val nyBostedskommuneErIFinnmarkEllerNordTroms = kommuneErIFinnmarkEllerNordTroms(bostedskommune)

        if (forrigeBostedskommuneErIFinnmarkEllerNordTroms == nyBostedskommuneErIFinnmarkEllerNordTroms) {
            secureLogger.info(
                "Person med ident $ident har ikke flyttet inn eller ut av Finnmark eller Nord-Troms, hopper ut av FinnmarkstilleggTask. " +
                    "Forrige bostedskommune: $sisteBostedsadresseFørHendelse, nåværende bostedskommune: $bostedskommune",
            )
            task.metadata["resultat"] = "HAR_IKKE_FLYTTET_INN_ELLER_UT"
            return
        }

        val erIkkeSøkerOgHarIkkeLøpendeBarnetrygd = baSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(ident).isEmpty()
        if (erIkkeSøkerOgHarIkkeLøpendeBarnetrygd) {
            secureLogger.info("Fant ingen fagsaker der ident $ident er søker eller har løpende barnetrygd, hopper ut av FinnmarkstilleggTask")
            task.metadata["resultat"] = "INGEN_LØPENDE_BARNETRYGD"
            return
        }

        secureLogger.info(
            "Person med ident $ident har flyttet inn eller ut av Finnmark eller Nord-Troms. " +
                "Gammel adresse: $forrigeBostedskommuneErIFinnmarkEllerNordTroms " +
                "Ny adresse: $nyBostedskommuneErIFinnmarkEllerNordTroms",
        )
        if (unleashNextMedContextService.isEnabled(FeatureToggleConfig.SEND_BOSTEDSADRESSE_HENDELSER_TIL_BA_SAK)) {
            baSakClient.sendFinnmarkstilleggTilBaSak(ident)
        }
        task.metadata["resultat"] = "HAR_FLYTTETT_INN_ELLER_UT"
    }

    companion object {
        const val TASK_STEP_TYPE = "finnmarkstilleggTask"
        private val secureLogger = LoggerFactory.getLogger("secureLogger")
    }
}

private fun Bostedsadresse?.erIFinnmarkEllerNordTroms(): Boolean =
    this != null &&
        vegadresse?.kommunenummer?.let { kommuneErIFinnmarkEllerNordTroms(it) }
            ?: matrikkeladresse?.kommunenummer?.let { kommuneErIFinnmarkEllerNordTroms(it) }
            ?: ukjentBosted?.bostedskommune?.let { kommuneErIFinnmarkEllerNordTroms(it) }
            ?: false

data class VurderFinnmarkstillleggTaskDTO(
    val ident: String,
    val bostedskommune: String?,
    val bostedskommuneFomDato: LocalDate?,
)
