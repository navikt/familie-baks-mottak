package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
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
        val sakssystemMarkering = task.payload

        val journalpost = journalpostClient.hentJournalpost(task.metadata["journalpostId"] as String)
        when (journalpost.journalstatus) {
            Journalstatus.MOTTATT -> {
                val journalføringsOppgaver = oppgaveClient.finnOppgaver(journalpost.journalpostId, Oppgavetype.Journalføring)
                val oppgaveTypeForEksisterendeOppgave: Oppgavetype? =
                        if (journalføringsOppgaver.isNotEmpty()) {
                            Oppgavetype.Journalføring
                        } else if (oppgaveClient.finnOppgaver(journalpost.journalpostId, Oppgavetype.Fordeling).isNotEmpty()) {
                            Oppgavetype.Fordeling
                        } else null

                if (oppgaveTypeForEksisterendeOppgave == null) {
                    val nyOppgave = oppgaveClient.opprettJournalføringsoppgave(journalpost = journalpost,
                                                                               beskrivelse = task.payload.takeIf { it.isNotEmpty() })
                    task.metadata["oppgaveId"] = "${nyOppgave.oppgaveId}"
                    taskRepository.saveAndFlush(task)
                    log.info("Oppretter ny journalførings-oppgave med id ${nyOppgave.oppgaveId} for journalpost ${journalpost.journalpostId}")
                } else {
                    log.info("Skipper oppretting av journalførings-oppgave. Fant åpen oppgave av type $oppgaveTypeForEksisterendeOppgave for ${journalpost.journalpostId}")
                    if (sakssystemMarkering.isNotEmpty()) {
                        journalføringsOppgaver.forEach { it.oppdaterOppgavebeskrivelse(sakssystemMarkering) }
                    }
                }
            }

            Journalstatus.JOURNALFOERT -> log.info("Skipper journalpost ${journalpost.journalpostId} som alt er i status JOURNALFOERT")

            else -> {
                val error = IllegalStateException(
                        "Journalpost ${journalpost.journalpostId} har endret status fra MOTTATT til ${journalpost.journalstatus.name}"
                )
                log.info("OpprettJournalføringOppgaveTask feilet.", error)
                throw error
            }
        }
    }

    private fun Oppgave.oppdaterOppgavebeskrivelse(sakssystemMarkering: String) {
        this.id?.let {
            log.info("Oppdaterer oppgavebeskrivelse for eksisterende oppgave $it: $sakssystemMarkering")
            oppgaveClient.oppdaterOppgaveBeskrivelse(it, sakssystemMarkering)
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "opprettJournalføringsoppgave"
    }
}

