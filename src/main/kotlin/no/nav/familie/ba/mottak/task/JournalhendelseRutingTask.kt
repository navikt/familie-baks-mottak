package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.FagsakDeltagerRolle.BARN
import no.nav.familie.ba.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.ba.mottak.integrasjoner.FagsakStatus.AVSLUTTET
import no.nav.familie.ba.mottak.integrasjoner.FagsakStatus.LØPENDE
import no.nav.familie.ba.mottak.integrasjoner.FagsakStatus.OPPRETTET
import no.nav.familie.ba.mottak.integrasjoner.Familierelasjonsrolle
import no.nav.familie.ba.mottak.integrasjoner.Identgruppe
import no.nav.familie.ba.mottak.integrasjoner.InfotrygdBarnetrygdClient
import no.nav.familie.ba.mottak.integrasjoner.IntegrasjonException
import no.nav.familie.ba.mottak.integrasjoner.Opphørsgrunn
import no.nav.familie.ba.mottak.integrasjoner.PdlClient
import no.nav.familie.ba.mottak.integrasjoner.RestFagsak
import no.nav.familie.ba.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.ba.mottak.integrasjoner.StatusKode
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkResponse
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import no.nav.familie.kontrakter.ba.infotrygd.Sak as SakDto
import no.nav.familie.kontrakter.ba.infotrygd.Stønad as StønadDto

@Service
@TaskStepBeskrivelse(taskStepType = JournalhendelseRutingTask.TASK_STEP_TYPE,
                     beskrivelse = "Håndterer ruting og markering av sakssystem")
class JournalhendelseRutingTask(private val pdlClient: PdlClient,
                                private val sakClient: SakClient,
                                private val infotrygdBarnetrygdClient: InfotrygdBarnetrygdClient,
                                private val taskRepository: TaskRepository,) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(JournalhendelseRutingTask::class.java)

    override fun doTask(task: Task) {
        val journalpostId = task.metadata["journalpostId"] as String
        val brukersIdent = task.metadata["personIdent"] as String?

        val (baSak, infotrygdSak) = brukersIdent?.run { søkEtterSakIBaSakOgInfotrygd(this) } ?: Pair(null, null)

        val sakssystemMarkering = when {
            baSak.finnes() && infotrygdSak.finnes() -> "Bruker har sak i både Infotrygd og BA-sak"
            baSak.finnes() -> "${baSak!!.part} har sak i BA-sak"
            infotrygdSak.finnes() -> "${infotrygdSak!!.part} har sak i Infotrygd"
            else -> "" // trenger ingen form for markering. Kan løses av begge systemer
        }

        when (task.payload) {
            "NAV_NO" -> {
                if (infotrygdSak.finnes() && !baSak.finnes()) {
                    log.info("Bruker har sak i Infotrygd. Overlater journalføring til BRUT001 og skipper opprettelse av oppgave for" +
                             " journalpost $journalpostId")
                    return
                } else if (!baSak.finnes() && brukersIdent != null) {
                    log.info("Bruker på journalpost $journalpostId har ikke pågående sak i BA-sak. Skipper derfor " +
                             "journalføring mot ny løsning i denne omgang.")
                    return
                }
            }
        }
        Task.nyTask(type = OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
                    payload = sakssystemMarkering,
                    properties = task.metadata).apply { taskRepository.save(this) }
    }

    private fun søkEtterSakIBaSakOgInfotrygd(brukersIdent: String): Pair<Sakspart?, Sakspart?> {
        val brukersIdenter = try {
            pdlClient.hentIdenter(brukersIdent).filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }.map { it.ident }
        } catch (e: IntegrasjonException) {
            return Pair(null, null)
        }
        val barnasIdenter = pdlClient.hentPersonMedRelasjoner(brukersIdent).familierelasjoner
                .filter { it.relasjonsrolle == Familierelasjonsrolle.BARN.name }
                .map { it.personIdent.id }
        val alleBarnasIdenter = barnasIdenter.flatMap { pdlClient.hentIdenter(it) }
                .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }
                .map { it.ident }

        return Pair(first = sakClient.hentRestFagsakDeltagerListe(brukersIdent, barnasIdenter).sakspart(sakClient),
                    second = infotrygdBarnetrygdClient.hentLøpendeUtbetalinger(brukersIdenter, alleBarnasIdenter).sakspart
                             ?: infotrygdBarnetrygdClient.hentSaker(brukersIdenter, alleBarnasIdenter).sakspart)
    }

    fun Sakspart?.finnes(): Boolean = this != null

    companion object {
        const val TASK_STEP_TYPE = "journalhendelseRuting"
    }
}

val InfotrygdSøkResponse<StønadDto>.sakspart: Sakspart?
    get() = if (bruker.isNotEmpty()) Sakspart.SØKER else if (barn.isNotEmpty()) Sakspart.ANNEN else null

val InfotrygdSøkResponse<SakDto>.sakspart: Sakspart?
    @JvmName("sakspart")
    get() = if (bruker.harSak()) Sakspart.SØKER else if (barn.harSak()) Sakspart.ANNEN else null

private fun List<SakDto>.harSak(): Boolean {
    val (sakerMedVedtak, sakerUtenVedtak) = this.partition { it.stønad != null }

    return sakerMedVedtak.let { saker -> saker.isNotEmpty() && !personErMigrert(saker) } ||
           sakerUtenVedtak.any { it.status != StatusKode.FB.name }
}

private fun personErMigrert(saker: List<no.nav.familie.kontrakter.ba.infotrygd.Sak>): Boolean {
    return saker.any {
        it.stønad!!.opphørsgrunn == Opphørsgrunn.MIGRERT.kode && it.vedtaksdato!!.isAfter(LocalDate.of(2020, 1, 1))
    }
}

enum class Sakspart(val part: String) {
    SØKER("Bruker"),
    ANNEN("Søsken"),
}

private fun List<RestFagsakDeltager>.sakspart(sakClient: SakClient): Sakspart? = when {
    any { it.rolle == FORELDER && it.harPågåendeSak(sakClient) } -> Sakspart.SØKER
    any { it.rolle == BARN && it.harPågåendeSak(sakClient) } -> Sakspart.ANNEN
    else -> null
}

private fun RestFagsakDeltager.harPågåendeSak(sakClient: SakClient): Boolean {
    return when (fagsakStatus) {
        OPPRETTET, LØPENDE -> true
        AVSLUTTET -> !sisteBehandlingHenlagtEllerTekniskOpphør(sakClient.hentRestFagsak(fagsakId))
    }
}

private fun sisteBehandlingHenlagtEllerTekniskOpphør(fagsak: RestFagsak): Boolean {
    val sisteBehandling = fagsak.behandlinger
                                  .sortedBy { it.opprettetTidspunkt }
                                  .findLast { it.steg == "BEHANDLING_AVSLUTTET" } ?: return false
    return sisteBehandling.type == "TEKNISK_OPPHØR" || sisteBehandling.resultat.startsWith("HENLAGT")
}