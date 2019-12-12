package no.nav.familie.ba.mottak.task

import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime


@Service
@TaskStepBeskrivelse(taskStepType = MottaFødselshendelseTask.TASK_STEP_TYPE, beskrivelse = "Motta fødselshendelse", maxAntallFeil = 10)
class MottaFødselshendelseTask(private val taskRepository: TaskRepository) : AsyncTaskStep {
    val log = LoggerFactory.getLogger(MottaFødselshendelseTask::class.java)

    override fun doTask(task: Task) {
        try {
            // vi går mot familie-integrasjoner, personopplysning v1/info
            // val personMedRelasjoner = tpsService.hentPersonMedRelasjoner(task.payload)
            log.info("MottaFødselshendelseTask kjører.")
        } catch (ex: RuntimeException) {
            log.info("Relasjon ikke funnet i TPS")
            task.triggerTid = LocalDateTime.now().plusHours(3)
            taskRepository.save(task)
            throw ex
        }
    }

    override fun onCompletion(task: Task) {
        log.info("MottaFødselshendelseTask er ferdig.")
    }

    companion object {
        const val TASK_STEP_TYPE = "mottaFødselshendelse"
    }
}