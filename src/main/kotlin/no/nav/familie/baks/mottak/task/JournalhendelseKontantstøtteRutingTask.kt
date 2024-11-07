package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlNotFoundException
import no.nav.familie.baks.mottak.journalføring.AutomatiskJournalføringKontantstøtteService
import no.nav.familie.baks.mottak.journalføring.JournalpostBrukerService
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
    beskrivelse = "Håndterer ruting og markering av sakssystem",
)
class JournalhendelseKontantstøtteRutingTask(
    private val ksSakClient: KsSakClient,
    private val taskService: TaskService,
    private val journalpostClient: JournalpostClient,
    private val automatiskJournalføringKontantstøtteService: AutomatiskJournalføringKontantstøtteService,
    private val journalpostBrukerService: JournalpostBrukerService,
) : AbstractJournalhendelseRutingTask(taskService) {
    private val tema = Tema.KON

    private val log: Logger = LoggerFactory.getLogger(JournalhendelseKontantstøtteRutingTask::class.java)

    override fun doTask(task: Task) {
        val journalpost = journalpostClient.hentJournalpost(task.metadata["journalpostId"] as String)

        if (journalpost.bruker == null) {
            opprettJournalføringOppgaveTask(
                sakssystemMarkering = "Ingen bruker er satt på journalpost. Kan ikke utlede om bruker har sak i Infotrygd eller KS-sak.",
                task = task,
            )
            return
        }

        val brukersIdent =
            try {
                journalpostBrukerService.tilPersonIdent(journalpost.bruker, tema)
            } catch (error: PdlNotFoundException) {
                opprettJournalføringOppgaveTask(
                    sakssystemMarkering = "Fant ingen aktiv personIdent for denne journalpost brukeren.",
                    task = task,
                )
                return
            }

        val fagsakId = ksSakClient.hentFagsaknummerPåPersonident(brukersIdent)

        val skalAutomatiskJournalføreJournalpost =
            automatiskJournalføringKontantstøtteService.skalAutomatiskJournalføres(
                journalpost,
                fagsakId,
            )

        if (skalAutomatiskJournalføreJournalpost) {
            log.info("Oppretter OppdaterOgFerdigstillJournalpostTask for journalpost med id ${journalpost.journalpostId}")

            Task(
                type = OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE,
                payload = journalpost.journalpostId,
                properties =
                    task.metadata.apply {
                        this["fagsakId"] = "$fagsakId"
                        this["personIdent"] = brukersIdent
                        this["sakssystemMarkering"] = ""
                    },
            ).apply { taskService.save(this) }
        } else {
            opprettJournalføringOppgaveTask(sakssystemMarkering = "", task = task)
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "journalhendelseKontantstøtteRuting"
    }
}
