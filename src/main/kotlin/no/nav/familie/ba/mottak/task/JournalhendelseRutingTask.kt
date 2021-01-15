package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

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

        when (task.payload) {
            "NAV_NO" -> {
                if (infotrygdSak.finnes() && baSak.finnes()) {
                    log.info("Bruker har sak i både Infotrygd og BA-sak. Dropper automatisk ferdigstilling og oppretter istedet " +
                             "manuell journalføringsoppgave for journalpost $journalpostId")
                    Task.nyTask(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
                                journalpostId,
                                task.metadata).also { taskRepository.save(it) }
                } else if (infotrygdSak.finnes()) {
                    log.info("Bruker har sak i Infotrygd. Overlater journalføring til BRUT001 og skipper opprettelse av BehandleSak-" +
                             "oppgave for journalpost $journalpostId")
                } else if (!baSak.finnes() && brukersIdent != null) {
                    log.info("Bruker på journalpost $journalpostId har ikke pågående sak i BA-sak. Skipper derfor " +
                             "journalføring og opprettelse av BehandleSak-oppgave mot ny løsning i denne omgang.")
                } else {
                    Task.nyTask(OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE,
                                journalpostId,
                                task.metadata).apply { taskRepository.save(this) }
                }
            }
            else -> {
                val sakssystemMarkering = when {
                    baSak.finnes() && infotrygdSak.finnes() -> "Bruker har sak i både Infotrygd og BA-sak"
                    baSak.finnes() -> "${baSak!!.part} har sak i BA-sak"
                    infotrygdSak.finnes() -> "${infotrygdSak!!.part} har sak i Infotrygd"
                    else -> "" // trenger ingen form for markering. Kan løses av begge systemer
                }
                Task.nyTask(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
                            sakssystemMarkering,
                            task.metadata).apply { taskRepository.save(this) }

            }
        }
    }

    private fun søkEtterSakIBaSakOgInfotrygd(brukersIdent: String): Pair<Sakspart?, Sakspart?> {
        val brukersIdenter =
                pdlClient.hentIdenter(brukersIdent).filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }.map { it.ident }
        val barnasIdenter = pdlClient.hentPersonMedRelasjoner(brukersIdent).familierelasjoner
                .filter { it.relasjonsrolle == Familierelasjonsrolle.BARN.name }
                .map { it.personIdent.id }
        val alleBarnasIdenter = barnasIdenter.flatMap { pdlClient.hentIdenter(it) }
                .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }
                .map { it.ident }

        return Pair(first = sakClient.hentPågåendeSakStatus(brukersIdent, barnasIdenter).baSak,
                    second = infotrygdBarnetrygdClient.hentLøpendeUtbetalinger(brukersIdenter, alleBarnasIdenter).resultat
                             ?: infotrygdBarnetrygdClient.hentSaker(brukersIdenter, alleBarnasIdenter).resultat)
    }

    companion object {
        const val TASK_STEP_TYPE = "journalhendelseRuting"
    }
}

val InfotrygdSøkResponse<StønadDto>.resultat: Sakspart?
    get() = if (bruker.isNotEmpty()) Sakspart.SØKER else if (barn.isNotEmpty()) Sakspart.ANNEN else null

val InfotrygdSøkResponse<SakDto>.resultat: Sakspart?
    @JvmName("getSakspart")
    get() = if (bruker.harSak()) Sakspart.SØKER else if (barn.harSak()) Sakspart.ANNEN else null

private fun List<SakDto>.harSak(): Boolean {
    val (sakerMedVedtak, sakerUtenVedtak) = this.partition { it.vedtak != null }

    return sakerMedVedtak.let { it.all { it.vedtak!!.opphørsgrunn != Opphørsgrunn.MIGRERT.kode } && it.isNotEmpty() }
           || sakerUtenVedtak.any { it.status != StatusKode.FB.name }
}

