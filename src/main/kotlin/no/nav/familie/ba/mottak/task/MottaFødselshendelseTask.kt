package no.nav.familie.ba.mottak.task

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.familie.ba.mottak.domene.BehandlingType
import no.nav.familie.ba.mottak.domene.NyBehandling
import no.nav.familie.ba.mottak.domene.personopplysning.Familierelasjon
import no.nav.familie.ba.mottak.domene.personopplysning.PersonIdent
import no.nav.familie.ba.mottak.domene.personopplysning.Personinfo
import no.nav.familie.ba.mottak.domene.personopplysning.RelasjonsRolleType
import no.nav.familie.ba.mottak.integrasjoner.PersonService
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime


@Service
@TaskStepBeskrivelse(taskStepType = MottaFødselshendelseTask.TASK_STEP_TYPE, beskrivelse = "Motta fødselshendelse", maxAntallFeil = 3)
class MottaFødselshendelseTask(private val taskRepository: TaskRepository, private val personService: PersonService) : AsyncTaskStep {
    val log: Logger = LoggerFactory.getLogger(MottaFødselshendelseTask::class.java)

    @Value("\${FØDSELSHENDELSE_REKJØRINGSINTERVALL_MINUTTER:1}")
    lateinit var rekjøringsintervall: String

    override fun doTask(task: Task) {
        try {
            log.info("MottaFødselshendelseTask kjører.")
            val personMedRelasjoner = personService.hentPersonMedRelasjoner(task.payload)
            log.info("kjønn: ${personMedRelasjoner.kjønn} fdato: ${personMedRelasjoner.fødselsdato}")

            val nesteTask = Task.nyTask(
                    SendTilSakTask.TASK_STEP_TYPE,
                    jacksonObjectMapper().writeValueAsString(NyBehandling (hentForsørger(personMedRelasjoner).id!!, arrayOf(task.payload), BehandlingType.FØRSTEGANGSBEHANDLING, null))
            )
            taskRepository.save(nesteTask)


        } catch (ex: RuntimeException) {
            log.info("Feil ved uthenting av personinfo.")
            task.triggerTid = LocalDateTime.now().plusMinutes(rekjøringsintervall.toLong())
            taskRepository.save(task)
            throw ex
        }
    }

    override fun onCompletion(task: Task) {
        log.info("MottaFødselshendelseTask er ferdig.")
    }

    fun hentForsørger(personinfo: Personinfo): PersonIdent {
       for (familierelasjon: Familierelasjon in personinfo.familierelasjoner!!) {
           if (familierelasjon.relasjonsrolle == RelasjonsRolleType.MORA) {
               return familierelasjon.personIdent
           }
       }
       // hvis vi ikke fant mora returner fara
        for (familierelasjon: Familierelasjon in personinfo.familierelasjoner!!) {
            if (familierelasjon.relasjonsrolle == RelasjonsRolleType.FARA) {
                return familierelasjon.personIdent
            }
        }
        log.warn("Fant hverken far eller mor...")
        throw IllegalStateException("Fant hverken mor eller far. Må behandles manuellt")
    }

    companion object {
        const val TASK_STEP_TYPE = "mottaFødselshendelse"
    }
}