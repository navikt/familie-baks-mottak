package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle.BARN
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus.AVSLUTTET
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus.LÅST
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus.LØPENDE
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus.OPPRETTET
import no.nav.familie.baks.mottak.integrasjoner.Identgruppe
import no.nav.familie.baks.mottak.integrasjoner.IntegrasjonException
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.baks.mottak.integrasjoner.PdlNotFoundException
import no.nav.familie.baks.mottak.integrasjoner.RestFagsak
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.baks.mottak.journalføring.AutomatiskJournalføringBarnetrygdService
import no.nav.familie.baks.mottak.journalføring.JournalpostBrukerService
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = JournalhendelseBarnetrygdRutingTask.TASK_STEP_TYPE,
    beskrivelse = "Håndterer ruting og markering av sakssystem",
)
class JournalhendelseBarnetrygdRutingTask(
    private val pdlClientService: PdlClientService,
    private val baSakClient: BaSakClient,
    private val taskService: TaskService,
    private val journalpostClient: JournalpostClient,
    private val automatiskJournalføringBarnetrygdService: AutomatiskJournalføringBarnetrygdService,
    private val journalpostBrukerService: JournalpostBrukerService,
) : AbstractJournalhendelseRutingTask(taskService) {
    private val tema = Tema.BAR

    private val log: Logger = LoggerFactory.getLogger(JournalhendelseBarnetrygdRutingTask::class.java)

    override fun doTask(task: Task) {
        val journalpost = journalpostClient.hentJournalpost(task.metadata["journalpostId"] as String)
        val brukersIdent = task.metadata["personIdent"] as String?

        val journalpostBruker = journalpost.bruker

        if (journalpostBruker == null) {
            opprettJournalføringOppgaveTask(
                sakssystemMarkering = "Ingen bruker er satt på journalpost. Kan ikke utlede om bruker har sak i BA-sak.",
                task = task,
            )
            return
        }

        val personIdent =
            try {
                journalpostBrukerService.tilPersonIdent(journalpostBruker, tema)
            } catch (error: PdlNotFoundException) {
                opprettJournalføringOppgaveTask(
                    sakssystemMarkering = "Fant ingen aktiv personIdent for denne journalpost brukeren.",
                    task = task,
                )
                return
            }

        Thread.sleep(1000L)

        val baSak = brukersIdent?.let { søkEtterSakIBaSak(it) }
        val sakssystemMarkering = baSak?.let { "${it.part} har sak i BA-sak" } ?: ""

        val skalAutomatiskJournalføreJournalpost =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        if (skalAutomatiskJournalføreJournalpost) {
            val fagsakId = baSakClient.hentFagsaknummerPåPersonident(personIdent)

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
            opprettJournalføringOppgaveTask(sakssystemMarkering = sakssystemMarkering, task = task)
        }
    }

    private fun søkEtterSakIBaSak(brukersIdent: String): Sakspart? {
        val brukersFnr =
            try {
                pdlClientService
                    .hentIdenter(brukersIdent, tema)
                    .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name && !it.historisk }
            } catch (e: IntegrasjonException) {
                return null
            }
        val barnasIdenter =
            pdlClientService
                .hentPersonMedRelasjoner(brukersIdent, tema)
                .forelderBarnRelasjoner
                .filter { it.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.BARN }
                .mapNotNull { it.relatertPersonsIdent }
        return baSakClient.hentRestFagsakDeltagerListe(brukersFnr.last().ident, barnasIdenter).harForelderEllerBarnPågåendeSak(baSakClient)
    }

    companion object {
        const val TASK_STEP_TYPE = "journalhendelseBarnetrygdRuting"
    }
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
        AVSLUTTET, LÅST -> !sisteBehandlingHenlagtEllerTekniskEndring(baSakClient.hentRestFagsak(fagsakId))
    }

private fun sisteBehandlingHenlagtEllerTekniskEndring(fagsak: RestFagsak): Boolean {
    val sisteBehandling =
        fagsak.behandlinger
            .sortedBy { it.opprettetTidspunkt }
            .findLast { it.steg == "BEHANDLING_AVSLUTTET" } ?: return false
    return sisteBehandling.type == "TEKNISK_ENDRING" || sisteBehandling.resultat.startsWith("HENLAGT")
}
