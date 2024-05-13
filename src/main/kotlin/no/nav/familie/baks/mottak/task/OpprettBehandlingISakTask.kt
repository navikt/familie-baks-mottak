package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.KontantstøtteOppgaveMapper
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.RestMinimalFagsak
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.tilbakekreving.Behandlingsstatus
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@TaskStepBeskrivelse(
    taskStepType = OpprettBehandlingISakTask.TASK_STEP_TYPE,
    beskrivelse = "Oppretter behandling i sak etter automatisk journalføring av søknad",
)
class OpprettBehandlingISakTask(
    private val journalpostClient: JournalpostClient,
    private val kontantstøtteOppgaveMapper: KontantstøtteOppgaveMapper,
    private val ksSakClient: KsSakClient,
) : AsyncTaskStep {
    val log: Logger = LoggerFactory.getLogger(OpprettBehandlingISakTask::class.java)

    override fun doTask(task: Task) {
        val journalpost = journalpostClient.hentJournalpost(task.metadata["journalpostId"] as String)
        val tema = Tema.valueOf(journalpost.tema!!)

        when (tema) {
            Tema.KON -> {
                val kategori = kontantstøtteOppgaveMapper.hentBehandlingstype(journalpost).name
                val fagsakId = task.metadata["fagsakId"] as String
                val brukersIdent = task.metadata["personIdent"] as String

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
        const val TASK_STEP_TYPE = "opprettBehandlingISak"
    }
}
