package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.PersonService
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service


@Service
@TaskStepBeskrivelse(taskStepType = MottaFødselshendelseTask.TASK_STEP_TYPE, beskrivelse = "Motta fødselshendelse", maxAntallFeil = 10)
class MottaFødselshendelseTask(private val taskRepository: TaskRepository, private val personService: PersonService) : AsyncTaskStep {
    val log = LoggerFactory.getLogger(MottaFødselshendelseTask::class.java)

    @Value("\${FØDSELSHENDELSE_REKJØRINGSINTERVALL_MINUTTER}")
    lateinit var rekjøringsintervall: String

    override fun doTask(task: Task) {
        try {
            log.info("MottaFødselshendelseTask kjører.")
            //val personMedRelasjoner = personService.hentPersonMedRelasjoner(task.payload)
            //log.info("kjønn: ${personMedRelasjoner.kjønn} fdato: ${personMedRelasjoner.fødselsdato}")

        } catch (ex: RuntimeException) {
            log.info("Relasjon ikke funnet i TPS. msg=${ex.message} stacktrace=${ex.stackTrace}")
            //task.triggerTid = LocalDateTime.now().plusMinutes(rekjøringsintervall.toLong())
            //taskRepository.save(task)
            throw ex
        }
    }

    override fun onCompletion(task: Task) {
        log.info("MottaFødselshendelseTask er ferdig.")
        val nesteTask = Task.nyTask(
                SendTilSakTask.TASK_STEP_TYPE,
                "{'fødselsnummer':'12345678901','barnasFødselsnummer':[${task.payload}],'behandlingType':'FØRSTEGANGSBEHANDLING'}"
        )
        taskRepository.save(nesteTask);
    }

    companion object {
        const val TASK_STEP_TYPE = "mottaFødselshendelse"
    }
}