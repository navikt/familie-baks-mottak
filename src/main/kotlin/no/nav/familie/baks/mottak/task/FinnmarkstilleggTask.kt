package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.kontrakter.ba.finnmarkstillegg.kommuneErIFinnmarkEllerNordTroms
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Status.KLAR_TIL_PLUKK
import no.nav.familie.prosessering.domene.Status.UBEHANDLET
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Properties

@Service
@TaskStepBeskrivelse(
    taskStepType = FinnmarkstilleggTask.TASK_STEP_TYPE,
    beskrivelse = "Sjekker om en adressehendelse skal trigge finnmarkstilleggbehandling i ba-sak",
    maxAntallFeil = 3,
    triggerTidVedFeilISekunder = 60,
)
class FinnmarkstilleggTask(
    private val pdlClientService: PdlClientService,
    private val baSakClient: BaSakClient,
    private val taskService: TaskService,
    private val environment: Environment,
) : AsyncTaskStep {
    override fun doTask(task: Task) {
        val payload = jsonMapper.readValue(task.payload, VurderFinnmarkstillleggTaskDTO::class.java)

        val ident = payload.ident
        val bostedskommune = payload.bostedskommune
        val bostedskommuneFomDato = payload.bostedskommuneFomDato

        if (bostedskommune == null || bostedskommuneFomDato == null) {
            secureLogger.info("Bostedskommune eller fom-dato er ikke satt for ident $ident, hopper ut av FinnmarkstilleggTask")
            task.metadata["resultat"] = "BOSTEDSKOMMUNE_ELLER_FOM_DATO_IKKE_SATT"
            return
        }

        val eksistererUkjørtTaskForIdent =
            taskService
                .finnTaskMedPayloadOgType(ident, TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE)
                ?.status in setOf(UBEHANDLET, KLAR_TIL_PLUKK)

        if (eksistererUkjørtTaskForIdent) {
            secureLogger.info("Det finnes allerede en ukjørt TriggFinnmarkstilleggbehandlingIBaSakTask for ident $ident, hopper ut av FinnmarkstilleggTask")
            task.metadata["resultat"] = "EKSISTERER_UKJØRT_TASK"
            return
        }

        val adresser = pdlClientService.hentPerson(ident, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse.filterNotNull()
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
        task.metadata["resultat"] = "HAR_FLYTTET_INN_ELLER_UT"
        taskService.save(
            Task(
                type = TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE,
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
                plusHours(1)
            } else {
                this
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

data class VurderFinnmarkstillleggTaskDTO(
    val ident: String,
    val bostedskommune: String?,
    val bostedskommuneFomDato: LocalDate?,
)
