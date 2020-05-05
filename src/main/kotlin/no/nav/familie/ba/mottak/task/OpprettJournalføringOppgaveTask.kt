package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.JournalpostClient
import no.nav.familie.ba.mottak.integrasjoner.Journalstatus
import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
                     beskrivelse = "Opprett journalføringsoppgave")
class OpprettJournalføringOppgaveTask(private val journalpostClient: JournalpostClient,
                                      private val oppgaveClient: OppgaveClient,
                                      private val taskRepository: TaskRepository) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(OpprettJournalføringOppgaveTask::class.java)

    override fun doTask(task: Task) {

        val journalpost = journalpostClient.hentJournalpost(task.payload)
        if (journalpost.journalstatus == Journalstatus.MOTTATT) {
            val oppgaveTypeForEksisterendeOppgave: Oppgavetype? =
                    if (oppgaveClient.finnOppgaver(journalpost.journalpostId, Oppgavetype.Journalføring).isNotEmpty()) {
                        Oppgavetype.Journalføring
                    } else if (oppgaveClient.finnOppgaver(journalpost.journalpostId, Oppgavetype.Fordeling).isNotEmpty()) {
                        Oppgavetype.Fordeling
                    } else null

            if (oppgaveTypeForEksisterendeOppgave == null) {
                val nyOppgave = oppgaveClient.opprettJournalføringsoppgave(journalpost)
                task.metadata["oppgaveId"] = "${nyOppgave.oppgaveId}"
                taskRepository.saveAndFlush(task)
                log.info("Oppretter ny journalførings-oppgave med id ${nyOppgave.oppgaveId} for journalpost ${journalpost.journalpostId}")
            } else {
                log.info("Skipper oppretting av journalførings-oppgave. Fant åpen oppgave av type $oppgaveTypeForEksisterendeOppgave for ${journalpost.journalpostId}")
            }
        } else {
            val error = IllegalStateException(
                    "Journalpost ${journalpost.journalpostId} har endret status fra MOTTATT til ${journalpost.journalstatus.name}"
            )
            log.info("OpprettJournalføringOppgaveTask feilet.", error)
            throw error
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "opprettJournalføringsoppgave"
    }
}

