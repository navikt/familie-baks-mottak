package no.nav.familie.baks.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.Bruker
import no.nav.familie.baks.mottak.integrasjoner.BrukerIdType
import no.nav.familie.baks.mottak.integrasjoner.Identgruppe
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdKontantstøtteClient
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.StonadDto
import no.nav.familie.baks.mottak.integrasjoner.erDigitalKanal
import no.nav.familie.baks.mottak.integrasjoner.erKontantstøtteSøknad
import no.nav.familie.baks.mottak.integrasjoner.finnesÅpenBehandlingPåFagsak
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.YearMonth

@Service
@TaskStepBeskrivelse(
    taskStepType = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
    beskrivelse = "Håndterer ruting og markering av sakssystem",
)
class JournalhendelseKontantstøtteRutingTask(
    private val pdlClient: PdlClient,
    private val infotrygdKontantstøtteClient: InfotrygdKontantstøtteClient,
    private val taskService: TaskService,
    private val journalpostClient: JournalpostClient,
    private val ksSakClient: KsSakClient,
    private val unleashService: UnleashNextMedContextService,
) : AsyncTaskStep {
    val log: Logger = LoggerFactory.getLogger(JournalhendelseKontantstøtteRutingTask::class.java)
    val enheterSomIkkeSkalHaAutomatiskJournalføring = listOf("4863")
    val sakssystemMarkeringCounter = mutableMapOf<String, Counter>()
    val tema = Tema.KON

    override fun doTask(task: Task) {
        val journalpost = journalpostClient.hentJournalpost(task.metadata["journalpostId"] as String)
        val brukersIdent = tilPersonIdent(journalpost.bruker!!, tema)
        val journalførendeEnhet = journalpost.journalforendeEnhet

        val fagsakId by lazy { ksSakClient.hentFagsaknummerPåPersonident(brukersIdent) }

        val harÅpenBehandlingIFagsak by lazy { ksSakClient.hentMinimalRestFagsak(fagsakId).finnesÅpenBehandlingPåFagsak() }
        val harLøpendeSakIInfotrygd = harLøpendeSakIInfotrygd(brukersIdent)
        val erKontantstøtteSøknad = journalpost.erKontantstøtteSøknad()
        val featureToggleForAutomatiskJournalføringSkruddPå =
            unleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_KONTANTSTØTTE_SØKNADER,
                defaultValue = false,
            )

        val sakssystemMarkering = hentSakssystemMarkering(harLøpendeSakIInfotrygd)

        val skalAutomatiskJournalføreJournalpost =
            featureToggleForAutomatiskJournalføringSkruddPå &&
                erKontantstøtteSøknad &&
                !harLøpendeSakIInfotrygd &&
                journalpost.erDigitalKanal() &&
                journalførendeEnhet !in enheterSomIkkeSkalHaAutomatiskJournalføring &&
                !harÅpenBehandlingIFagsak

        if (skalAutomatiskJournalføreJournalpost) {
            log.info("Oppretter OppdaterOgFerdigstillJournalpostTask for journalpost med id ${journalpost.journalpostId}")

            Task(
                type = OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE,
                payload = journalpost.journalpostId,
                properties =
                    task.metadata.apply {
                        this["fagsakId"] = "$fagsakId"
                        this["personIdent"] = brukersIdent
                        this["sakssystemMarkering"] = sakssystemMarkering
                    },
            ).apply { taskService.save(this) }
        } else {
            Task(
                type = OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
                payload = sakssystemMarkering,
                properties = task.metadata,
            ).apply { taskService.save(this) }
        }
    }

    private fun hentSakssystemMarkering(harLøpendeSakIInfotrygd: Boolean): String {
        val sakssystemMarkering =
            when {
                harLøpendeSakIInfotrygd -> {
                    incrementSakssystemMarkering("Infotrygd")
                    "Et eller flere av barna har løpende sak i Infotrygd"
                }

                else -> {
                    incrementSakssystemMarkering("Ingen")
                    ""
                }
            }
        return sakssystemMarkering
    }

    private fun hentBarnasIdenterFraPdl(
        brukersIdent: String,
        tema: Tema,
    ): List<String> {
        val barnasIdenter =
            pdlClient
                .hentPersonMedRelasjoner(brukersIdent, tema)
                .forelderBarnRelasjoner
                .filter { it.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.BARN }
                .mapNotNull { it.relatertPersonsIdent }

        return barnasIdenter
            .flatMap { pdlClient.hentIdenter(it, Tema.KON) }
            .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }
            .map { it.ident }
    }

    private fun incrementSakssystemMarkering(saksystem: String) {
        if (!sakssystemMarkeringCounter.containsKey(saksystem)) {
            sakssystemMarkeringCounter[saksystem] =
                Metrics.counter("kontantstotte.ruting.saksystem", "saksystem", saksystem)
        }
        sakssystemMarkeringCounter[saksystem]!!.increment()
    }

    private fun harLøpendeSakIInfotrygd(brukersIdent: String): Boolean {
        val barnasIdenterFraPdl = hentBarnasIdenterFraPdl(brukersIdent, tema)

        return if (infotrygdKontantstøtteClient.harKontantstøtteIInfotrygd(barnasIdenterFraPdl)) {
            infotrygdKontantstøtteClient.hentPerioderMedKontantstotteIInfotrygdByBarn(barnasIdenterFraPdl).data.harPågåendeSak()
        } else {
            false
        }
    }

    private fun tilPersonIdent(
        bruker: Bruker,
        tema: Tema,
    ): String =
        when (bruker.type) {
            BrukerIdType.AKTOERID -> pdlClient.hentPersonident(bruker.id, tema)
            else -> bruker.id
        }

    companion object {
        const val TASK_STEP_TYPE = "journalhendelseKontantstøtteRuting"
    }
}

private fun List<StonadDto>.harPågåendeSak(): Boolean = any { it.erPågåendeSak() }

private fun StonadDto.erPågåendeSak(): Boolean =
    when {
        tom == null -> true
        tom.isBefore(YearMonth.now()) -> false
        tom.isAfter(YearMonth.now()) -> true
        else -> true
    }
