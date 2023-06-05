package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.Bruker
import no.nav.familie.baks.mottak.integrasjoner.BrukerIdType
import no.nav.familie.baks.mottak.integrasjoner.DokarkivClient
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.Journalstatus
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE,
    beskrivelse = "Legger til Sak og ferdigstiller journalpost",
)
class OppdaterOgFerdigstillJournalpostTask(
    private val journalpostClient: JournalpostClient,
    private val dokarkivClient: DokarkivClient,
    private val sakClient: SakClient,
    private val taskService: TaskService,
    private val pdlClient: PdlClient,
) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(OppdaterOgFerdigstillJournalpostTask::class.java)

    override fun doTask(task: Task) {
        val journalpost = journalpostClient.hentJournalpost(task.payload)
            .takeUnless { it.bruker == null } ?: error("Journalpost ${task.payload} mangler bruker")

        when (journalpost.journalstatus) {
            Journalstatus.MOTTATT -> {
                val fagsakId = sakClient.hentSaksnummer(tilPersonIdent(journalpost.bruker!!, journalpost.tema))
                runCatching { // forsøk å journalføre automatisk
                    dokarkivClient.oppdaterJournalpostSak(journalpost, fagsakId)
                    dokarkivClient.ferdigstillJournalpost(journalpost.journalpostId)
                }.fold(
                    onSuccess = {
                        task.metadata["fagsakId"] = fagsakId
                        log.info("Har oppdatert og ferdigstilt journalpost ${journalpost.journalpostId}")
                    },
                    onFailure = {
                        log.warn(
                            "Automatisk ferdigstilling feilet. Oppretter ny journalføringsoppgave for journalpost " +
                                "${journalpost.journalpostId}.",
                        )
                        Task(
                            OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
                            journalpost.journalpostId,
                            task.metadata,
                        ).also { taskService.save(it) }
                        return
                    },
                )
            }
            Journalstatus.JOURNALFOERT -> log.info(
                "Skipper oppdatering og ferdigstilling av " +
                    "journalpost ${journalpost.journalpostId} som alt er ferdig journalført",
            )
            else -> error("Uventet journalstatus ${journalpost.journalstatus} for journalpost ${journalpost.journalpostId}")
        }

        val nyTask = Task(
            type = OpprettBehandleSakOppgaveTask.TASK_STEP_TYPE,
            payload = task.payload,
            properties = task.metadata.apply {
                this["fagsystem"] = "BA"
            },
        )
        taskService.save(nyTask)
    }

    private fun tilPersonIdent(bruker: Bruker, tema: String?): String {
        return when (bruker.type) {
            BrukerIdType.AKTOERID -> pdlClient.hentPersonident(bruker.id, tema?.let { Tema.valueOf(tema) } ?: Tema.BAR)
            else -> bruker.id
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "oppdaterOgFerdigstillJournalpost"
    }
}
