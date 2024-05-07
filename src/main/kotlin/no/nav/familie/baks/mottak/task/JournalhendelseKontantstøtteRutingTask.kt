package no.nav.familie.baks.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.integrasjoner.Bruker
import no.nav.familie.baks.mottak.integrasjoner.BrukerIdType
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.IdentInformasjon
import no.nav.familie.baks.mottak.integrasjoner.Identgruppe
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdKontantstøtteClient
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.baks.mottak.integrasjoner.StonadDto
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.YearMonth
import java.util.Properties

@Service
@TaskStepBeskrivelse(
    taskStepType = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
    beskrivelse = "Håndterer ruting og markering av sakssystem",
)
class JournalhendelseKontantstøtteRutingTask(
    private val pdlClient: PdlClient,
    private val infotrygdKontantstøtteClient: InfotrygdKontantstøtteClient,
    private val taskService: TaskService,
    private val journalpostClient: JournalpostClient,
    private val ksSakClient: KsSakClient,
) : AsyncTaskStep {
    val log: Logger = LoggerFactory.getLogger(JournalhendelseKontantstøtteRutingTask::class.java)
    val sakssystemMarkeringCounter = mutableMapOf<String, Counter>()
    private val tema = Tema.KON

    override fun doTask(task: Task) {
        val brukersIdent = task.metadata["personIdent"] as String

        val journalpost = journalpostClient.hentJournalpost(task.metadata["journalpostId"] as String)

        val barnasIdenter =
            pdlClient.hentPersonMedRelasjoner(brukersIdent, Tema.KON).forelderBarnRelasjoner
                .filter { it.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.BARN }
                .map { it.relatertPersonsIdent }
                .filterNotNull()
        val alleBarnasIdenter =
            barnasIdenter.flatMap { pdlClient.hentIdenter(it, Tema.KON) }
                .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }
                .map { it.ident }

        val harLøpendeSakIInfotrygd = søkEtterSakIInfotrygd(alleBarnasIdenter)

        val brukersFnr =
            pdlClient.hentIdenter(brukersIdent, tema)
                .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }

        val harLøpendeSakIKsSak by lazy {
            harLøpendeSakIKsSak(brukersFnr, barnasIdenter)
        }

        val erKontantstøtteSøknad = journalpost.dokumenter?.find { it.brevkode == "NAV 34-00.08" } != null

        if (erKontantstøtteSøknad && !harLøpendeSakIInfotrygd && !harLøpendeSakIKsSak) {
            val fagsakId = ksSakClient.hentSaksnummer(tilPersonIdent(journalpost.bruker!!, journalpost.tema))

            Task(
                type = OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE,
                payload = journalpost.journalpostId,
                properties =
                    Properties().apply {
                        this["fagsakId"] = fagsakId
                        this["tema"] = Tema.KON
                    },
            ).apply { taskService.save(this) }
        } else {
            val sakssystemMarkering =
                when {
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
                properties = task.metadata,
            ).apply { taskService.save(this) }
        }
    }

    private fun harLøpendeSakIKsSak(
        brukersFnr: List<IdentInformasjon>,
        barnasIdenter: List<String>,
    ): Boolean {
        val harLøpendeSakIKsSak =
            ksSakClient
                .hentRestFagsakDeltagerListe(brukersFnr.last().ident, barnasIdenter)
                .harForelderEllerBarnPågåendeSak()
        return harLøpendeSakIKsSak
    }

    private fun List<RestFagsakDeltager>.harForelderEllerBarnPågåendeSak(): Boolean =
        this.any { it.rolle == FagsakDeltagerRolle.FORELDER || it.rolle == FagsakDeltagerRolle.BARN && it.harPågåendeSak() }

    private fun RestFagsakDeltager.harPågåendeSak(): Boolean {
        return when (fagsakStatus) {
            FagsakStatus.OPPRETTET, FagsakStatus.LØPENDE -> true
            FagsakStatus.AVSLUTTET -> false
        }
    }

    private fun incrementSakssystemMarkering(saksystem: String) {
        if (!sakssystemMarkeringCounter.containsKey(saksystem)) {
            sakssystemMarkeringCounter[saksystem] =
                Metrics.counter("kontantstotte.ruting.saksystem", "saksystem", saksystem)
        }
        sakssystemMarkeringCounter[saksystem]!!.increment()
    }

    private fun søkEtterSakIInfotrygd(alleBarnasIdenter: List<String>): Boolean {
        return if (infotrygdKontantstøtteClient.harKontantstøtteIInfotrygd(alleBarnasIdenter)) {
            infotrygdKontantstøtteClient.hentPerioderMedKontantstotteIInfotrygdByBarn(alleBarnasIdenter).data.harPågåendeSak()
        } else {
            false
        }
    }

    private fun tilPersonIdent(
        bruker: Bruker,
        tema: String?,
    ): String {
        return when (bruker.type) {
            BrukerIdType.AKTOERID -> pdlClient.hentPersonident(bruker.id, tema?.let { Tema.valueOf(tema) } ?: Tema.BAR)
            else -> bruker.id
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
