package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.*

import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository

import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE, beskrivelse = "Legger til Sak og ferdigstiller journalpost")
class OppdaterOgFerdigstillJournalpostTask(private val journalpostClient: JournalpostClient,
                                           private val dokarkivClient: DokarkivClient,
                                           private val sakClient: SakClient,
                                           private val aktørClient: AktørClient,
                                           private val taskRepository: TaskRepository) : AsyncTaskStep {

    override fun doTask(task: Task) {
        val journalpost = journalpostClient.hentJournalpost(task.payload)
            .takeUnless { it.bruker == null } ?: throw error("Journalpost ${task.payload} mangler bruker")
        val fagsakId = sakClient.hentSaksnummer(tilPersonIdent(journalpost.bruker!!))

        dokarkivClient.oppdaterJournalpostSak(journalpost, fagsakId.toString())
        dokarkivClient.ferdigstillJournalpost(journalpost.journalpostId)

        task.metadata["fagsakId"] = fagsakId
        task.metadata["personIdent"] = journalpost.bruker?.id
        task.metadata["journalpostId"] = journalpost.journalpostId
        taskRepository.saveAndFlush(task)
    }

    override fun onCompletion(task: Task) {
        val nyTask = Task.nyTask(
            type = OpprettBehandleSakOppgaveTask.TASK_STEP_TYPE,
            payload = task.payload,
            properties = task.metadata
        )
        taskRepository.save(nyTask)
    }

    private fun tilPersonIdent(bruker: Bruker): String {
        return when (bruker.type) {
            BrukerIdType.AKTOERID -> aktørClient.hentPersonident(bruker.id)
            else -> bruker.id
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "oppdaterOgFerdigstillJournalpost"
    }
}