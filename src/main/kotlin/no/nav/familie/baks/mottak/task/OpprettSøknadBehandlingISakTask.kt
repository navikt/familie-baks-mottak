package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.BarnetrygdOppgaveMapper
import no.nav.familie.baks.mottak.integrasjoner.BehandlingType
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.KontantstøtteOppgaveMapper
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.RestMinimalFagsak
import no.nav.familie.baks.mottak.integrasjoner.Søknadsinfo
import no.nav.familie.baks.mottak.integrasjoner.finnesÅpenBehandlingPåFagsak
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@TaskStepBeskrivelse(
    taskStepType = OpprettSøknadBehandlingISakTask.TASK_STEP_TYPE,
    beskrivelse = "Oppretter behandling i sak etter automatisk journalføring av søknad",
)
class OpprettSøknadBehandlingISakTask(
    private val journalpostClient: JournalpostClient,
    private val kontantstøtteOppgaveMapper: KontantstøtteOppgaveMapper,
    private val barnetrygdOppgaveMapper: BarnetrygdOppgaveMapper,
    private val ksSakClient: KsSakClient,
    private val baSakClient: BaSakClient,
) : AsyncTaskStep {
    val log: Logger = LoggerFactory.getLogger(OpprettSøknadBehandlingISakTask::class.java)

    override fun doTask(task: Task) {
        val journalpost = journalpostClient.hentJournalpost(task.metadata["journalpostId"] as String)
        val fagsakId = task.metadata["fagsakId"] as String
        val brukersIdent = task.metadata["personIdent"] as String
        val tema = Tema.valueOf(journalpost.tema!!)

        when (tema) {
            Tema.KON -> {
                val fagsak = ksSakClient.hentMinimalRestFagsak(fagsakId.toLong())
                val finnesÅpenBehandlingPåFagsak = fagsak.finnesÅpenBehandlingPåFagsak()

                if (finnesÅpenBehandlingPåFagsak) {
                    log.info("Finnes allerede åpen behandling på fagsak $fagsakId m/ tema $tema. Hopper over opprettelsen av ny behandling")
                } else {
                    val behandlingType = utledBehandlingstype(fagsak)
                    val kategori = kontantstøtteOppgaveMapper.utledBehandlingKategoriFraSøknad(journalpost).name

                    log.info("Oppretter ny $kategori $behandlingType behandling i ks-sak")

                    ksSakClient.opprettBehandling(
                        kategori = kategori,
                        behandlingÅrsak = "SØKNAD",
                        søkersIdent = brukersIdent,
                        søknadMottattDato = journalpost.datoMottatt ?: LocalDateTime.now(),
                        behandlingType = behandlingType,
                    )
                }
            }

            Tema.BAR -> {
                val fagsak = baSakClient.hentMinimalRestFagsak(fagsakId.toLong())

                val finnesÅpenBehandlingPåFagsak = fagsak.finnesÅpenBehandlingPåFagsak()

                if (finnesÅpenBehandlingPåFagsak) {
                    log.info("Finnes allerede åpen behandling på fagsak $fagsakId m/ tema $tema. Hopper over opprettelsen av ny behandling")
                } else {
                    val behandlingType = utledBehandlingstype(fagsak)
                    val kategori = barnetrygdOppgaveMapper.utledBehandlingKategoriFraSøknad(journalpost)
                    val underkategori = barnetrygdOppgaveMapper.utledBehandlingUnderkategoriFraSøknad(journalpost)
                    val brevkode = journalpost.dokumenter?.firstOrNull { it.brevkode != null }?.brevkode

                    log.info("Oppretter ny $kategori $behandlingType behandling i ba-sak")

                    baSakClient.opprettBehandling(
                        kategori = kategori,
                        underkategori = underkategori,
                        behandlingÅrsak = "SØKNAD",
                        søkersIdent = brukersIdent,
                        søknadMottattDato = journalpost.datoMottatt ?: LocalDateTime.now(),
                        behandlingType = behandlingType,
                        fagsakId = fagsakId.toLong(),
                        søknadsinfo = Søknadsinfo(journalpostId = journalpost.journalpostId, brevkode = brevkode, erDigital = journalpost.erDigitalKanal()),
                    )
                }
            }

            else -> throw IllegalStateException("$tema ikke støttet")
        }
    }

    private fun utledBehandlingstype(fagsak: RestMinimalFagsak): BehandlingType {
        val erFagsakLøpende = fagsak.status == FagsakStatus.LØPENDE

        val type =
            if (!erFagsakLøpende) {
                BehandlingType.FØRSTEGANGSBEHANDLING
            } else {
                BehandlingType.REVURDERING
            }
        return type
    }

    companion object {
        const val TASK_STEP_TYPE = "OpprettSøknadBehandlingISakTask"
    }
}
