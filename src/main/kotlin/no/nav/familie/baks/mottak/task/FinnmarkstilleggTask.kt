package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.ba.finnmarkstillegg.kommuneErIFinnmarkEllerNordTroms
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

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
) : AsyncTaskStep {
    override fun doTask(task: Task) {
        val ident = task.payload

        val harIkkeLøpendeBarnetrygd = baSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(ident).isEmpty()
        if (harIkkeLøpendeBarnetrygd) {
            secureLogger.info("Fant ingen løpende barnetrygd for ident $ident, hopper ut av FinnmarkstilleggTask")
            task.metadata["resultat"] = "INGEN_LØPENDE_BARNETRYGD"
            return
        }

        val adresser = pdlClient.hentPerson(ident, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse.filterNotNull()
        if (adresser.isEmpty()) {
            secureLogger.info("Fant ingen bostedsadresser for ident $ident, hopper ut av FinnmarkstilleggTask")
            task.metadata["resultat"] = "INGEN_BOSTEDSADRESSE"
            return
        }

        val sorterteAdresser = adresser.sortedByDescending { it.gyldigFraOgMed }
        val sisteBostedsadresse = sorterteAdresser.firstOrNull()
        val nestSisteBostedsadresse = sorterteAdresser.drop(1).firstOrNull()
        val harFlyttetInnEllerUtAvFinnmarkEllerNordTroms =
            sisteBostedsadresse.erIFinnmarkEllerNordTroms() &&
                (nestSisteBostedsadresse == null || !nestSisteBostedsadresse.erIFinnmarkEllerNordTroms()) ||
                !sisteBostedsadresse.erIFinnmarkEllerNordTroms() &&
                nestSisteBostedsadresse.erIFinnmarkEllerNordTroms()

        if (harFlyttetInnEllerUtAvFinnmarkEllerNordTroms) {
            secureLogger.info(
                "Person med ident $ident har flyttet inn eller ut av Finnmark eller Nord-Troms. " +
                    "Siste adresse: ${sisteBostedsadresse?.vegadresse?.kommunenummer ?: sisteBostedsadresse?.ukjentBosted?.bostedskommune} " +
                    "Nest siste adresse: ${nestSisteBostedsadresse?.vegadresse?.kommunenummer ?: nestSisteBostedsadresse?.ukjentBosted?.bostedskommune}",
            )
            baSakClient.sendFinnmarkstilleggTilBaSak(ident)
            task.metadata["resultat"] = "HAR_FLYTTETT_INN_ELLER_UT"
        } else {
            secureLogger.info("Person med ident $ident har ikke flyttet inn eller ut av Finnmark eller Nord-Troms.")
            task.metadata["resultat"] = "HAR_IKKE_FLYTTETT_INN_ELLER_UT"
        }
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
