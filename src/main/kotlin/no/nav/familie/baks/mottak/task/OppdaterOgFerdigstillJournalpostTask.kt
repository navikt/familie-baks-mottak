package no.nav.familie.baks.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.integrasjoner.DokarkivClient
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE,
    beskrivelse = "Oppdaterer journalpost med fagsak og ferdigstiller journalpost",
)
class OppdaterOgFerdigstillJournalpostTask(
    private val journalpostClient: JournalpostClient,
    private val dokarkivClient: DokarkivClient,
    private val taskService: TaskService,
) : AsyncTaskStep {
    val log: Logger = LoggerFactory.getLogger(OppdaterOgFerdigstillJournalpostTask::class.java)
    val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

    val barnetrygdFeiledeFerdigstilteJournalpostCounter: Counter = Metrics.counter("barnetrygd.journalpost.ferdigstill.feil")
    val kontantstøtteFeiledeFerdigstilteJournalpostCounter: Counter = Metrics.counter("kontantstotte.journalpost.ferdigstill.feil")

    override fun doTask(task: Task) {
        val journalpost =
            journalpostClient
                .hentJournalpost(task.payload)
                .takeUnless { it.bruker == null } ?: error("Journalpost ${task.payload} mangler bruker")

        val fagsakId = task.metadata["fagsakId"] as String
        val tema = Tema.valueOf(journalpost.tema!!)

        when (journalpost.journalstatus) {
            Journalstatus.MOTTATT -> {
                runCatching {
                    // forsøk å journalføre automatisk
                    dokarkivClient.oppdaterJournalpostSak(journalpost, fagsakId, tema)
                    dokarkivClient.ferdigstillJournalpost(journalpost.journalpostId)
                }.fold(
                    onSuccess = {
                        log.info("Har oppdatert og automatisk ferdigstilt journalpost ${journalpost.journalpostId}")
                        log.info("Oppretter OpprettSøknadBehandlingISakTask for fagsak $fagsakId m/ tema $tema")
                        Task(
                            type = OpprettSøknadBehandlingISakTask.TASK_STEP_TYPE,
                            payload = journalpost.journalpostId,
                            properties = task.metadata,
                        ).also(taskService::save)
                    },
                    onFailure = {
                        when (tema) {
                            Tema.BAR -> barnetrygdFeiledeFerdigstilteJournalpostCounter.increment()
                            Tema.KON -> kontantstøtteFeiledeFerdigstilteJournalpostCounter.increment()
                            else -> log.info("Ukjent tema ${tema.name}")
                        }

                        log.warn("Automatisk ferdigstilling feilet. Oppretter ny journalføringsoppgave for journalpost ${journalpost.journalpostId}.")
                        secureLogger.warn("Automatisk ferdigstilling feilet. Oppretter ny journalføringsoppgave for journalpost ${journalpost.journalpostId}.", it)

                        Task(
                            type = OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
                            payload = task.metadata["sakssystemMarkering"] as String,
                            properties =
                                task.metadata.apply {
                                    this["tema"] = tema.toString()
                                },
                        ).also(taskService::save)

                        return@doTask
                    },
                )
            }

            Journalstatus.JOURNALFOERT ->
                log.info(
                    "Skipper oppdatering og ferdigstilling av " +
                        "journalpost ${journalpost.journalpostId} som alt er ferdig journalført",
                )

            else -> error("Uventet journalstatus ${journalpost.journalstatus} for journalpost ${journalpost.journalpostId}")
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "oppdaterOgFerdigstillJournalpost"
    }
}
