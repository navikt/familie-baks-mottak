package no.nav.familie.baks.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.ArbeidsfordelingClient
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.Bruker
import no.nav.familie.baks.mottak.integrasjoner.BrukerIdType
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle.BARN
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus.AVSLUTTET
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus.LØPENDE
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus.OPPRETTET
import no.nav.familie.baks.mottak.integrasjoner.Identgruppe
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdBarnetrygdClient
import no.nav.familie.baks.mottak.integrasjoner.IntegrasjonException
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.Opphørsgrunn
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.RestFagsak
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.baks.mottak.integrasjoner.StatusKode
import no.nav.familie.baks.mottak.integrasjoner.erBarnetrygdSøknad
import no.nav.familie.baks.mottak.integrasjoner.erDigitalKanal
import no.nav.familie.baks.mottak.integrasjoner.finnesÅpenBehandlingPåFagsak
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkResponse
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import no.nav.familie.kontrakter.ba.infotrygd.Sak as SakDto
import no.nav.familie.kontrakter.ba.infotrygd.Stønad as StønadDto

@Service
@TaskStepBeskrivelse(
    taskStepType = JournalhendelseBarnetrygdRutingTask.TASK_STEP_TYPE,
    beskrivelse = "Håndterer ruting og markering av sakssystem",
)
class JournalhendelseBarnetrygdRutingTask(
    private val pdlClient: PdlClient,
    private val baSakClient: BaSakClient,
    private val infotrygdBarnetrygdClient: InfotrygdBarnetrygdClient,
    private val taskService: TaskService,
    private val unleashService: UnleashNextMedContextService,
    private val journalpostClient: JournalpostClient,
    private val arbeidsfordelingClient: ArbeidsfordelingClient,
) : AsyncTaskStep {
    private val tema = Tema.BAR

    val log: Logger = LoggerFactory.getLogger(JournalhendelseBarnetrygdRutingTask::class.java)
    val sakssystemMarkeringCounter = mutableMapOf<String, Counter>()
    val enheterSomIkkeSkalHaAutomatiskJournalføring = listOf("4863")

    override fun doTask(task: Task) {
        val brukersIdent = task.metadata["personIdent"] as String?
        val journalpost = journalpostClient.hentJournalpost(task.metadata["journalpostId"] as String)

        val erBarnetrygdSøknad = journalpost.erBarnetrygdSøknad()

        val (baSak, infotrygdSak) = brukersIdent?.run { søkEtterSakIBaSakOgInfotrygd(this) } ?: Pair(null, null)

        val brukerHarFagsakIBaSak = baSak.finnes()
        val brukerHarSakIInfotrygd = infotrygdSak.finnes()
        val featureToggleForAutomatiskJournalføringSkruddPå =
            unleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER,
                defaultValue = false,
            )

        val personIdent by lazy { tilPersonIdent(journalpost.bruker!!, tema) }
        val fagsakId by lazy { baSakClient.hentFagsaknummerPåPersonident(personIdent) }
        val harÅpenBehandlingIFagsak by lazy { baSakClient.hentMinimalRestFagsak(fagsakId.toLong()).finnesÅpenBehandlingPåFagsak() }

        val sakssystemMarkering = hentSakssystemMarkering(brukerHarFagsakIBaSak, brukerHarSakIInfotrygd, baSak, infotrygdSak)

        val skalAutomatiskJournalføreJournalpost =
            featureToggleForAutomatiskJournalføringSkruddPå &&
                erBarnetrygdSøknad &&
                !brukerHarSakIInfotrygd &&
                arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(personIdent, tema).enhetId !in enheterSomIkkeSkalHaAutomatiskJournalføring &&
                journalpost.erDigitalKanal() &&
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

    private fun hentSakssystemMarkering(
        brukerHarFagsakIBaSak: Boolean,
        brukerHarSakIInfotrygd: Boolean,
        baSak: Sakspart?,
        infotrygdSak: Sakspart?,
    ): String {
        val sakssystemMarkering =
            when {
                brukerHarFagsakIBaSak && brukerHarSakIInfotrygd -> {
                    incrementSakssystemMarkering("Begge")
                    "Bruker har sak i både Infotrygd og BA-sak"
                }

                brukerHarFagsakIBaSak -> {
                    incrementSakssystemMarkering("BA_SAK")
                    "${baSak!!.part} har sak i BA-sak"
                }

                brukerHarSakIInfotrygd -> {
                    incrementSakssystemMarkering("Infotrygd")
                    "${infotrygdSak!!.part} har sak i Infotrygd"
                }

                else -> {
                    incrementSakssystemMarkering("Ingen")
                    ""
                } // trenger ingen form for markering. Kan løses av begge systemer
            }
        return sakssystemMarkering
    }

    private fun incrementSakssystemMarkering(saksystem: String) {
        if (!sakssystemMarkeringCounter.containsKey(saksystem)) {
            sakssystemMarkeringCounter[saksystem] = Metrics.counter("barnetrygd.ruting.saksystem", "saksystem", saksystem)
        }
        sakssystemMarkeringCounter[saksystem]!!.increment()
    }

    private fun søkEtterSakIBaSakOgInfotrygd(brukersIdent: String): Pair<Sakspart?, Sakspart?> {
        val (brukersHistoriskeFnr, brukersFnr) =
            try {
                pdlClient
                    .hentIdenter(brukersIdent, tema)
                    .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }
                    .partition { it.historisk }
            } catch (e: IntegrasjonException) {
                return Pair(null, null)
            }
        val brukersIdenter = brukersFnr.plus(brukersHistoriskeFnr).map { it.ident }
        val barnasIdenter =
            pdlClient
                .hentPersonMedRelasjoner(brukersIdent, tema)
                .forelderBarnRelasjoner
                .filter { it.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.BARN }
                .mapNotNull { it.relatertPersonsIdent }
        val alleBarnasIdenter =
            barnasIdenter
                .flatMap { pdlClient.hentIdenter(it, tema) }
                .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }
                .map { it.ident }

        return Pair(
            first = baSakClient.hentRestFagsakDeltagerListe(brukersFnr.last().ident, barnasIdenter).harForelderEllerBarnPågåendeSak(baSakClient),
            second =
                infotrygdBarnetrygdClient.hentLøpendeUtbetalinger(brukersIdenter, alleBarnasIdenter).sakspart
                    ?: infotrygdBarnetrygdClient.hentSaker(brukersIdenter, alleBarnasIdenter).sakspart,
        )
    }

    fun Sakspart?.finnes(): Boolean = this != null

    private fun tilPersonIdent(
        bruker: Bruker,
        tema: Tema,
    ): String =
        when (bruker.type) {
            BrukerIdType.AKTOERID -> pdlClient.hentPersonident(bruker.id, tema)
            else -> bruker.id
        }

    companion object {
        const val TASK_STEP_TYPE = "journalhendelseBarnetrygdRuting"
    }
}

val InfotrygdSøkResponse<StønadDto>.sakspart: Sakspart?
    get() =
        if (bruker.isNotEmpty()) {
            Sakspart.SØKER
        } else if (barn.isNotEmpty()) {
            Sakspart.ANNEN
        } else {
            null
        }

val InfotrygdSøkResponse<SakDto>.sakspart: Sakspart?
    @JvmName("sakspart")
    get() =
        if (bruker.harSak()) {
            Sakspart.SØKER
        } else if (barn.harSak()) {
            Sakspart.ANNEN
        } else {
            null
        }

private fun List<SakDto>.harSak(): Boolean {
    val (sakerMedVedtak, sakerUtenVedtak) = this.partition { it.stønad != null }

    return sakerMedVedtak.let { saker -> saker.isNotEmpty() && !personErMigrert(saker) } ||
        sakerUtenVedtak.any { it.status != StatusKode.FB.name }
}

private fun personErMigrert(saker: List<no.nav.familie.kontrakter.ba.infotrygd.Sak>): Boolean =
    saker.any {
        it.stønad!!.opphørsgrunn == Opphørsgrunn.MIGRERT.kode && it.vedtaksdato!!.isAfter(LocalDate.of(2020, 1, 1))
    }

enum class Sakspart(
    val part: String,
) {
    SØKER("Bruker"),
    ANNEN("Søsken"),
}

private fun List<RestFagsakDeltager>.harForelderEllerBarnPågåendeSak(baSakClient: BaSakClient): Sakspart? =
    when {
        any { it.rolle == FORELDER && it.harPågåendeSak(baSakClient) } -> Sakspart.SØKER
        any { it.rolle == BARN && it.harPågåendeSak(baSakClient) } -> Sakspart.ANNEN
        else -> null
    }

private fun RestFagsakDeltager.harPågåendeSak(baSakClient: BaSakClient): Boolean =
    when (fagsakStatus) {
        OPPRETTET, LØPENDE -> true
        AVSLUTTET -> !sisteBehandlingHenlagtEllerTekniskOpphør(baSakClient.hentRestFagsak(fagsakId))
    }

private fun sisteBehandlingHenlagtEllerTekniskOpphør(fagsak: RestFagsak): Boolean {
    val sisteBehandling =
        fagsak.behandlinger
            .sortedBy { it.opprettetTidspunkt }
            .findLast { it.steg == "BEHANDLING_AVSLUTTET" } ?: return false
    return sisteBehandling.type == "TEKNISK_OPPHØR" || sisteBehandling.resultat.startsWith("HENLAGT")
}
