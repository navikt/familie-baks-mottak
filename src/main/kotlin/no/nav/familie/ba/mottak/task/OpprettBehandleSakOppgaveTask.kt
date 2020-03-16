package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.JournalpostClient
import no.nav.familie.ba.mottak.integrasjoner.Journalstatus
import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = OpprettBehandleSakOppgaveTask.TASK_STEP_TYPE, beskrivelse = "Opprett \"BehandleSak\"-oppgave")
class OpprettBehandleSakOppgaveTask(private val journalpostClient: JournalpostClient,
                                    private val oppgaveClient: OppgaveClient,
                                    private val taskRepository: TaskRepository) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(OpprettBehandleSakOppgaveTask::class.java)

    override fun doTask(task: Task) {
        val journalpost = journalpostClient.hentJournalpost(task.payload)

        if (journalpost.journalstatus == Journalstatus.FERDIGSTILT) {
            task.metadata["oppgaveId"] =
                    "${oppgaveClient.opprettBehandleSakOppgave(journalpost).oppgaveId}"
            task.metadata["personIdent"] = journalpost.bruker?.id
            task.metadata["journalpostId"] = journalpost.journalpostId
            task.metadata["fagsakId"] = journalpost.sak?.fagsakId
            taskRepository.saveAndFlush(task)
        } else {
            throw error("Kan ikke opprette oppgave før tilhørende journalpost ${journalpost.journalpostId} er ferdigstilt")
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "opprettBehandleSakoppgave"
    }
}