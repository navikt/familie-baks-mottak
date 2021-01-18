package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = VurderLivshendelseTask.TASK_STEP_TYPE, beskrivelse = "Vurder livshendelse")
class VurderLivshendelseTask(private val oppgaveClient: OppgaveClient,
                             private val taskRepository: TaskRepository) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(OpprettBehandleSakOppgaveTask::class.java)

    override fun doTask(task: Task) {
        val payload = objectMapper.readValue(task.payload, VurderLivshendelseTaskDTO::class.java)
        //hent familierelasjoner


        //val oppgaver = oppgaveClient.finnOppgaver(journalpost.journalpostId, null)
        task.metadata["oppgaveId"] = "${oppgaveClient.opprettVurderLivshendelseOppgave(payload.personIdent).oppgaveId}"
        taskRepository.saveAndFlush(task)
    }

    companion object {
        const val TASK_STEP_TYPE = "opprettBehandleSakoppgave"
    }
}

data class VurderLivshendelseTaskDTO(val personIdent: String, val type: String)