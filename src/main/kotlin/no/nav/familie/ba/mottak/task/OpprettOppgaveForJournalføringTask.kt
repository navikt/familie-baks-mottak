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
@TaskStepBeskrivelse(taskStepType = OpprettOppgaveForJournalføringTask.TASK_STEP_TYPE, beskrivelse = "Opprett journalføringsoppgave")
class OpprettOppgaveForJournalføringTask(private val journalpostClient: JournalpostClient,
                                         private val oppgaveClient: OppgaveClient,
                                         private val taskRepository: TaskRepository) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(OpprettOppgaveForJournalføringTask::class.java)

    override fun doTask(task: Task) {
        val journalpost = journalpostClient.hentJournalpost(task.payload)

        if (journalpost.journalstatus == Journalstatus.MOTTATT) {
            task.metadata["oppgaveId"] = "${oppgaveClient.opprettJournalføringsoppgave(journalpost).oppgaveId}"
            task.metadata["personIdent"] = journalpost.bruker?.id
            task.metadata["journalpostId"] = journalpost.journalpostId
            taskRepository.saveAndFlush(task)
        } else {
            log.info("Ingen oppgave opprettet da journalpost ${journalpost.journalpostId} ikke har status MOTTATT lenger.")
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "opprettJournalføringsoppgave"
    }
}