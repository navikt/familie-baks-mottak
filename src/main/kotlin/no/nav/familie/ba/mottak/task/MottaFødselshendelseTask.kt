package no.nav.familie.ba.mottak.task

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.domene.NyBehandling
import no.nav.familie.ba.mottak.domene.personopplysning.Familierelasjon
import no.nav.familie.ba.mottak.domene.personopplysning.PersonIdent
import no.nav.familie.ba.mottak.domene.personopplysning.Personinfo
import no.nav.familie.ba.mottak.domene.personopplysning.RelasjonsRolleType
import no.nav.familie.ba.mottak.integrasjoner.PersonService
import no.nav.familie.ba.mottak.util.erDnummer
import no.nav.familie.ba.mottak.util.nesteGyldigeArbeidsdag
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
@TaskStepBeskrivelse(taskStepType = MottaFødselshendelseTask.TASK_STEP_TYPE,
                     beskrivelse = "Motta fødselshendelse",
                     maxAntallFeil = 3)
class MottaFødselshendelseTask(private val taskRepository: TaskRepository,
                               private val personService: PersonService,
                               @Value("\${FØDSELSHENDELSE_REKJØRINGSINTERVALL_MINUTTER}") private val rekjøringsintervall: Long)
    : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(MottaFødselshendelseTask::class.java)
    val barnHarDnrCounter: Counter = Metrics.counter("barnetrygd.hendelse.ignorert.barn.har.dnr.eller.fdatnr")
    val forsørgerHarDnrCounter: Counter = Metrics.counter("barnetrygd.hendelse.ignorert.forsørger.har.dnr.eller.fdatnr")

    override fun doTask(task: Task) {
        val barnetsId = task.payload

        if (erDnummer(PersonIdent(barnetsId)) || erFDatnummer(PersonIdent(barnetsId))) {
            log.info("Ignorer fødselshendelse: Barnet har DNR/FDAT-nummer")
            barnHarDnrCounter.increment()
            return
        }

        try {
            val personMedRelasjoner = personService.hentPersonMedRelasjoner(barnetsId)

            val forsørger = hentForsørger(personMedRelasjoner)

            if (erDnummer(forsørger) || erFDatnummer(forsørger)) {
                log.info("Ignorer fødselshendelse: Barnets forsørger har DNR/FDAT-nummer")
                forsørgerHarDnrCounter.increment()
                return
            }

            task.metadata["forsørger"] = forsørger.id!!
            val nesteTask = Task.nyTask(
                    SendTilSakTask.TASK_STEP_TYPE,
                    jacksonObjectMapper().writeValueAsString(NyBehandling(forsørger.id!!,
                            arrayOf(barnetsId))),
                    task.metadata
            )

            taskRepository.save(nesteTask)

        } catch (ex: RuntimeException) {
            log.info("MottaFødselshendelseTask feilet.")
            task.triggerTid = nesteGyldigeArbeidsdag(rekjøringsintervall)
            taskRepository.save(task)
            throw ex
        }
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
        throw IllegalStateException("Fant hverken mor eller far. Må behandles manuelt")
    }

    fun erFDatnummer(personIdent: PersonIdent): Boolean {
        return personIdent.id?.substring(6)?.toInt()!! == 0
    }

    companion object {
        const val TASK_STEP_TYPE = "mottaFødselshendelse"
    }
}
