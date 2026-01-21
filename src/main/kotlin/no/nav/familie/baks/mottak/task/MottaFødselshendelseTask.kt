package no.nav.familie.baks.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.domene.NyBehandling
import no.nav.familie.baks.mottak.domene.personopplysning.Person
import no.nav.familie.baks.mottak.domene.personopplysning.PersonIdent
import no.nav.familie.baks.mottak.domene.personopplysning.harAdresseGradering
import no.nav.familie.baks.mottak.domene.personopplysning.harBostedsadresse
import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.baks.mottak.integrasjoner.erUtenforNorge
import no.nav.familie.baks.mottak.util.erBostNummer
import no.nav.familie.baks.mottak.util.erDnummer
import no.nav.familie.baks.mottak.util.erFDatnummer
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.kontrakter.felles.personopplysning.ForelderBarnRelasjon
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonObjectMapper

@Service
@TaskStepBeskrivelse(
    taskStepType = MottaFødselshendelseTask.TASK_STEP_TYPE,
    beskrivelse = "Motta fødselshendelse",
    maxAntallFeil = 3,
)
class MottaFødselshendelseTask(
    private val taskService: TaskService,
    private val pdlClientService: PdlClientService,
) : AsyncTaskStep {
    val log: Logger = LoggerFactory.getLogger(MottaFødselshendelseTask::class.java)
    val barnHarDnrCounter: Counter = Metrics.counter("barnetrygd.hendelse.ignorert.barn.har.dnr.eller.fdatnr")
    val forsørgerHarDnrCounter: Counter = Metrics.counter("barnetrygd.hendelse.ignorert.forsorger.har.dnr.eller.fdatnr")
    val barnetManglerBostedsadresse: Counter = Metrics.counter("barnetrygd.hendelse.ignorert.bostedsadresse.null")
    val fødselIgnorertFødelandCounter: Counter = Metrics.counter("hendelse.ignorert.fodeland.nor")

    override fun doTask(task: Task) {
        val barnetsId = task.payload

        if (erDnummer(barnetsId) || erFDatnummer(barnetsId) || erBostNummer(barnetsId)) {
            log.info("Ignorer fødselshendelse: Barnet har DNR/FDAT/BOST-nummer")
            barnHarDnrCounter.increment()
            return
        }

        val pdlPersonData = pdlClientService.hentPerson(barnetsId, "hentperson-fødested", Tema.BAR)
        if (pdlPersonData.fødested.first().erUtenforNorge()) {
            log.info("Fødeland er ikke Norge. Ignorerer hendelse")
            fødselIgnorertFødelandCounter.increment()
            return
        }

        try {
            val personMedRelasjoner = pdlClientService.hentPersonMedRelasjoner(barnetsId, Tema.BAR)

            val morsIdent = hentMor(personMedRelasjoner)

            if (morsIdent != null) {
                if (erDnummer(morsIdent) || erFDatnummer(morsIdent) || erBostNummer(morsIdent)) {
                    log.info("Ignorer fødselshendelse: Barnets forsørger har DNR/FDAT/BOST-nummer")
                    forsørgerHarDnrCounter.increment()
                    return
                }

                if (skalFiltrerePåBostedsadresse(personMedRelasjoner)) {
                    log.info("Ignorer fødselshendelse: Barnet har ukjent bostedsadresse. task=${task.id}")
                    barnetManglerBostedsadresse.increment()
                    return
                }

                task.metadata["morsIdent"] = morsIdent.id
                val nesteTask =
                    Task(
                        SendTilBaSakTask.TASK_STEP_TYPE,
                        jacksonObjectMapper().writeValueAsString(
                            NyBehandling(
                                morsIdent = morsIdent.id,
                                barnasIdenter = arrayOf(barnetsId),
                            ),
                        ),
                        task.metadata,
                    )

                taskService.save(nesteTask)
            } else {
                log.info("Skipper fødselshendelse fordi man ikke fant en mor")
            }
        } catch (ex: RuntimeException) {
            throw ex
        }
    }

    fun hentMor(personinfo: Person): PersonIdent? {
        for (forelderBarnRelasjon: ForelderBarnRelasjon in personinfo.forelderBarnRelasjoner) {
            if (forelderBarnRelasjon.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.MOR) {
                return forelderBarnRelasjon.relatertPersonsIdent?.let { PersonIdent(it) }
            }
        }
        return null
    }

    fun skalFiltrerePåBostedsadresse(person: Person): Boolean =
        if (person.harAdresseGradering()) {
            false
        } else {
            !person.harBostedsadresse()
        }

    companion object {
        const val TASK_STEP_TYPE = "mottaFødselshendelse"
    }
}
