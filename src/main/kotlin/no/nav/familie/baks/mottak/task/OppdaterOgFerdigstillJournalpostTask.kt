package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.DokarkivClient
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.Journalstatus
import no.nav.familie.baks.mottak.integrasjoner.KontantstøtteOppgaveMapper
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.RestMinimalFagsak
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.tilbakekreving.Behandlingsstatus
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@TaskStepBeskrivelse(
    taskStepType = OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE,
    beskrivelse = "Legger til Sak og ferdigstiller journalpost",
)
class OppdaterOgFerdigstillJournalpostTask(
    private val journalpostClient: JournalpostClient,
    private val dokarkivClient: DokarkivClient,
    private val taskService: TaskService,
    private val ksSakClient: KsSakClient,
    private val kontantstøtteOppgaveMapper: KontantstøtteOppgaveMapper,
) : AsyncTaskStep {
    val log: Logger = LoggerFactory.getLogger(OppdaterOgFerdigstillJournalpostTask::class.java)

    override fun doTask(task: Task) {
        val journalpost =
            journalpostClient.hentJournalpost(task.payload)
                .takeUnless { it.bruker == null } ?: error("Journalpost ${task.payload} mangler bruker")

        val fagsakId = task.metadata["fagsakId"] as String
        val tema = Tema.valueOf(task.metadata["tema"] as String)
        val brukersIdent = task.metadata["personIdent"] as String

        when (journalpost.journalstatus) {
            Journalstatus.MOTTATT -> {
                runCatching { // forsøk å journalføre automatisk
                    dokarkivClient.oppdaterJournalpostSak(journalpost, fagsakId, tema)
                    dokarkivClient.ferdigstillJournalpost(journalpost.journalpostId)

                    when (tema) {
                        Tema.KON -> {
                            val kategori = kontantstøtteOppgaveMapper.hentBehandlingstype(journalpost).name
                            val fagsak = ksSakClient.hentMinimalRestFagsak(fagsakId.toLong())

                            val type = utledBehandlingstype(fagsak)

                            ksSakClient.opprettBehandlingIKsSak(
                                kategori = kategori,
                                behandlingÅrsak = "SØKNAD",
                                søkersIdent = brukersIdent,
                                søknadMottattDato = journalpost.datoMottatt ?: LocalDateTime.now(),
                                type = type,
                            )
                        }
                        else -> throw IllegalStateException("$tema ikke støttet")
                    }
                }.fold(
                    onSuccess = {
                        task.metadata["fagsakId"] = fagsakId
                        log.info("Har oppdatert og ferdigstilt journalpost ${journalpost.journalpostId}")
                    },
                    onFailure = {
                        log.warn(
                            "Automatisk ferdigstilling feilet. Oppretter ny journalføringsoppgave for journalpost " +
                                "${journalpost.journalpostId}.",
                        )
                        Task(
                            OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
                            journalpost.journalpostId,
                            // Husk å legge til journalpostid hvis det feiler
                            task.metadata,
                        ).also { taskService.save(it) }
                        return
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

    private fun utledBehandlingstype(fagsak: RestMinimalFagsak): String {
        val erFagsakLøpendeOgAktiveBehandlingerAvsluttet =
            fagsak.behandlinger.any { behandling ->
                val kanOppretteBehandling = behandling.status == Behandlingsstatus.AVSLUTTET.name && behandling.aktiv
                fagsak.status != FagsakStatus.LØPENDE && kanOppretteBehandling
            }

        val type =
            if (erFagsakLøpendeOgAktiveBehandlingerAvsluttet || fagsak.behandlinger.isEmpty()) {
                "FØRSTEGANGSBEHANDLING"
            } else {
                "REVURDERING"
            }
        return type
    }

    companion object {
        const val TASK_STEP_TYPE = "oppdaterOgFerdigstillJournalpost"
    }
}
