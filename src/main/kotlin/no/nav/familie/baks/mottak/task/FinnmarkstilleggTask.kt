package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.ba.finnmarkstillegg.kommuneErIFinnmarkEllerNordTroms
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = FinnmarkstilleggTask.TASK_STEP_TYPE,
    beskrivelse = "Finnmarkstillegg",
    maxAntallFeil = 3,
    triggerTidVedFeilISekunder = 60,
)
class FinnmarkstilleggTask(
    private val pdlClient: PdlClient,
    private val taskService: TaskService,
) : AsyncTaskStep {
    override fun doTask(task: Task) {
        val ident = task.payload
        val adresser = pdlClient.hentPerson(ident, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse.filterNotNull()
        if (adresser.isEmpty()) {
            secureLogger.info("Fant ingen bostedsadresser for ident $ident")
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

        task.metadata["harFlyttet"] = if (harFlyttetInnEllerUtAvFinnmarkEllerNordTroms) "Ja" else "Nei"
        taskService.save(task)
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
