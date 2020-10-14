package no.nav.familie.ba.mottak.task

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.domene.NyBehandling
import no.nav.familie.ba.mottak.domene.personopplysning.Familierelasjon
import no.nav.familie.ba.mottak.domene.personopplysning.Person
import no.nav.familie.ba.mottak.domene.personopplysning.PersonIdent
import no.nav.familie.ba.mottak.integrasjoner.PersonClient
import no.nav.familie.ba.mottak.integrasjoner.TPSPersonClient
import no.nav.familie.ba.mottak.util.erBostNummer
import no.nav.familie.ba.mottak.util.erDnummer
import no.nav.familie.ba.mottak.util.erFDatnummer
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
                     maxAntallFeil = 10)
class MottaFødselshendelseTask(private val taskRepository: TaskRepository,
                               private val tpsPersonClient: TPSPersonClient,
                               private val personClient: PersonClient,
                               @Value("\${FØDSELSHENDELSE_REKJØRINGSINTERVALL_MINUTTER}") private val rekjøringsintervall: Long)
    : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(MottaFødselshendelseTask::class.java)
    val barnHarDnrCounter: Counter = Metrics.counter("barnetrygd.hendelse.ignorert.barn.har.dnr.eller.fdatnr")
    val forsørgerHarDnrCounter: Counter = Metrics.counter("barnetrygd.hendelse.ignorert.forsørger.har.dnr.eller.fdatnr")
    val barnetManglerBostedsadresse: Counter = Metrics.counter("barnetrygd.hendelse.ignorert.bostedsadresse.null")


    override fun doTask(task: Task) {
        val barnetsId = task.payload

        if (erDnummer(barnetsId) || erFDatnummer(barnetsId) || erBostNummer(barnetsId)) {
            log.info("Ignorer fødselshendelse: Barnet har DNR/FDAT/BOST-nummer")
            barnHarDnrCounter.increment()
            return
        }

        try {
            val personMedRelasjoner = personClient.hentPersonMedRelasjoner(barnetsId)

            // denne kreves for at infotrygd ikke skal få fødselshendelser som ikke ligger i TPS
            // vil feile ved forsinkelse i TPS feks i helger og helligdager.
            // TODO fjerne når barnetrygd er ute av infotrygd
            tpsPersonClient.hentPersonMedRelasjoner(barnetsId)

            val morsIdent = hentMor(personMedRelasjoner)

            if (morsIdent != null) {
                if (erDnummer(morsIdent) || erFDatnummer(morsIdent) || erBostNummer(morsIdent)) {
                    log.info("Ignorer fødselshendelse: Barnets forsørger har DNR/FDAT/BOST-nummer")
                    forsørgerHarDnrCounter.increment()
                    return
                }

                if (personMedRelasjoner.bostedsadresse == null || personMedRelasjoner.bostedsadresse.ukjentBosted != null) {
                    log.info("Ignorer fødselshendelse: Barnet har ukjent bostedsadresse. task=${task.id}")
                    barnetManglerBostedsadresse.increment()
                    return
                }

                task.metadata["morsIdent"] = morsIdent.id
                val nesteTask = Task.nyTask(
                        SendTilSakTask.TASK_STEP_TYPE,
                        jacksonObjectMapper().writeValueAsString(NyBehandling(morsIdent = morsIdent.id,
                                                                              barnasIdenter = arrayOf(barnetsId))),
                        task.metadata
                )

                taskRepository.save(nesteTask)
            } else {
                log.info("Skipper fødselshendelse fordi man ikke fant en mor")
            }
        } catch (ex: RuntimeException) {
            log.info("MottaFødselshendelseTask feilet.")
            task.triggerTid = LocalDateTime.now().plusMinutes(rekjøringsintervall)
            taskRepository.save(task)
            throw ex
        }
    }

    fun hentMor(personinfo: Person): PersonIdent? {
        for (familierelasjon: Familierelasjon in personinfo.familierelasjoner) {
            if (familierelasjon.relasjonsrolle == "MOR") {
                return familierelasjon.personIdent
            }
        }
        return null
    }


    companion object {

        const val TASK_STEP_TYPE = "mottaFødselshendelse"
    }
}
