package no.nav.familie.baks.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.integrasjoner.Identgruppe
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdKontantstøtteClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.StonadDto
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.YearMonth

@Service
@TaskStepBeskrivelse(
    taskStepType = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
    beskrivelse = "Håndterer ruting og markering av sakssystem"
)
class JournalhendelseKontantstøtteRutingTask(
    private val pdlClient: PdlClient,
    private val infotrygdKontantstøtteClient: InfotrygdKontantstøtteClient,
    private val taskService: TaskService
) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(JournalhendelseKontantstøtteRutingTask::class.java)
    val sakssystemMarkeringCounter = mutableMapOf<String, Counter>()

    override fun doTask(task: Task) {
        val brukersIdent = task.metadata["personIdent"] as String?

        val harLøpendeSakIInfotrygd = brukersIdent?.run { søkEtterSakIInfotrygd(this) } ?: false

        val sakssystemMarkering = when {
            harLøpendeSakIInfotrygd -> {
                incrementSakssystemMarkering("Infotrygd")
                "Et eller flere av barna har løpende sak i Infotrygd"
            }

            else -> {
                incrementSakssystemMarkering("Ingen")
                ""
            }
        }

        Task(
            type = OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
            payload = sakssystemMarkering,
            properties = task.metadata
        ).apply { taskService.save(this) }
    }

    private fun incrementSakssystemMarkering(saksystem: String) {
        if (!sakssystemMarkeringCounter.containsKey(saksystem)) {
            sakssystemMarkeringCounter[saksystem] =
                Metrics.counter("kontantstotte.ruting.saksystem", "saksystem", saksystem)
        }
        sakssystemMarkeringCounter[saksystem]!!.increment()
    }

    private fun søkEtterSakIInfotrygd(brukersIdent: String): Boolean {
        val barnasIdenter = pdlClient.hentPersonMedRelasjoner(brukersIdent).forelderBarnRelasjoner
            .filter { it.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.BARN }
            .map { it.relatertPersonsIdent }
            .filterNotNull()
        val alleBarnasIdenter = barnasIdenter.flatMap { pdlClient.hentIdenter(it) }
            .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }
            .map { it.ident }

        return if (infotrygdKontantstøtteClient.harKontantstøtteIInfotrygd(alleBarnasIdenter)) {
            infotrygdKontantstøtteClient.hentPerioderMedKontantstøtteIInfotrygd(alleBarnasIdenter).data.harPågåendeSak()
        } else {
            false
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "journalhendelseKontantstøtteRuting"
    }
}

private fun List<StonadDto>.harPågåendeSak(): Boolean = any { it.erPågåendeSak() }

private fun StonadDto.erPågåendeSak(): Boolean {
    return when {
        tom == null -> true
        tom.isBefore(YearMonth.now()) -> false
        tom.isAfter(YearMonth.now()) -> true
        else -> true
    }
}
