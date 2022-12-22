package no.nav.familie.baks.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
    beskrivelse = "Håndterer ruting og markering av sakssystem"
)
class JournalhendelseKontantstøtteRutingTask(
    private val pdlClient: PdlClient,
    private val taskService: TaskService
) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(JournalhendelseKontantstøtteRutingTask::class.java)
    val sakssystemMarkeringCounter = mutableMapOf<String, Counter>()

    override fun doTask(task: Task) {
        val brukersIdent = task.metadata["personIdent"] as String?

        val (ksSak, infotrygdSak) = brukersIdent?.run { søkEtterSakIKsSakOgInfotrygd(this) } ?: Pair(null, null)

        val sakssystemMarkering = when {
            ksSak.finnes() && infotrygdSak.finnes() -> {
                incrementSakssystemMarkering("Begge")
                "Bruker har sak i både Infotrygd og KS-sak"
            }

            ksSak.finnes() -> {
                incrementSakssystemMarkering("KS_SAK")
                "${ksSak!!.part} har sak i BA-sak"
            }

            infotrygdSak.finnes() -> {
                incrementSakssystemMarkering("Infotrygd")
                "${infotrygdSak!!.part} har sak i Infotrygd"
            }

            else -> {
                incrementSakssystemMarkering("Ingen")
                ""
            } // trenger ingen form for markering. Kan løses av begge systemer
        }

        Task(
            type = OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
            payload = sakssystemMarkering,
            properties = task.metadata
        ).apply { taskService.save(this) }
    }

    private fun incrementSakssystemMarkering(saksystem: String) {
        if (!sakssystemMarkeringCounter.containsKey(saksystem)) {
            sakssystemMarkeringCounter[saksystem] =
                Metrics.counter("kontantstotte.ruting.saksystem", "saksystem", saksystem)
        }
        sakssystemMarkeringCounter[saksystem]!!.increment()
    }

    private fun søkEtterSakIKsSakOgInfotrygd(brukersIdent: String): Pair<Sakspart?, Sakspart?> {
        return Pair(null, null)
        // TODO: Hent eventuelle saker fra infotrygd og kontantstøtte
//        val brukersIdenter = try {
//            pdlClient.hentIdenter(brukersIdent).filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }
//                .map { it.ident }
//        } catch (e: IntegrasjonException) {
//            return Pair(null, null)
//        }
//        val barnasIdenter = pdlClient.hentPersonMedRelasjoner(brukersIdent).forelderBarnRelasjoner
//            .filter { it.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.BARN }
//            .map { it.relatertPersonsIdent }
//            .filterNotNull()
//        val alleBarnasIdenter = barnasIdenter.flatMap { pdlClient.hentIdenter(it) }
//            .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }
//            .map { it.ident }
//
//        return Pair(
//            first = baSakClient.hentRestFagsakDeltagerListe(brukersIdent, barnasIdenter).sakspart(baSakClient),
//            second = infotrygdBarnetrygdClient.hentLøpendeUtbetalinger(brukersIdenter, alleBarnasIdenter).sakspart
//                ?: infotrygdBarnetrygdClient.hentSaker(brukersIdenter, alleBarnasIdenter).sakspart
//        )
    }

    fun Sakspart?.finnes(): Boolean = this != null

    companion object {

        const val TASK_STEP_TYPE = "journalhendelseKontantstøtteRuting"
    }
}

// TODO: Fjern eller legg inn tilsvarende når vi får lagt inn KSSakKlient
// private fun List<RestFagsakDeltager>.sakspart(baSakClient: BaSakClient): Sakspart? = when {
//    any { it.rolle == FORELDER && it.harPågåendeSak(baSakClient) } -> Sakspart.SØKER
//    any { it.rolle == BARN && it.harPågåendeSak(baSakClient) } -> Sakspart.ANNEN
//    else -> null
// }
//
// private fun RestFagsakDeltager.harPågåendeSak(baSakClient: BaSakClient): Boolean {
//    return when (fagsakStatus) {
//        OPPRETTET, LØPENDE -> true
//        AVSLUTTET -> !sisteBehandlingHenlagtEllerTekniskOpphør(baSakClient.hentRestFagsak(fagsakId))
//    }
// }
//
// private fun sisteBehandlingHenlagtEllerTekniskOpphør(fagsak: RestFagsak): Boolean {
//    val sisteBehandling = fagsak.behandlinger
//        .sortedBy { it.opprettetTidspunkt }
//        .findLast { it.steg == "BEHANDLING_AVSLUTTET" } ?: return false
//    return sisteBehandling.type == "TEKNISK_OPPHØR" || sisteBehandling.resultat.startsWith("HENLAGT")
// }
