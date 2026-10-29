package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggle
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleService
import no.nav.familie.baks.mottak.integrasjoner.AutovedtakSøknadFeilårsak
import no.nav.familie.baks.mottak.integrasjoner.AutovedtakSøknadStatistikkDto
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.BaksVersjonertSøknadClientService
import no.nav.familie.baks.mottak.integrasjoner.BarnetrygdOppgaveMapper
import no.nav.familie.baks.mottak.integrasjoner.BehandlingType
import no.nav.familie.baks.mottak.integrasjoner.BehandlingÅrsak
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.KontantstøtteOppgaveMapper
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.RestMinimalFagsak
import no.nav.familie.baks.mottak.integrasjoner.Søknadsinfo
import no.nav.familie.baks.mottak.integrasjoner.finnesÅpenBehandlingPåFagsak
import no.nav.familie.baks.mottak.journalføring.AdressebeskyttelesesgraderingService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.harDokumentasjonsbehovUtenVedlegg
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.jsonMapper
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
    taskStepType = OpprettSøknadBehandlingISakTask.TASK_STEP_TYPE,
    beskrivelse = "Oppretter behandling i sak etter automatisk journalføring av søknad",
)
class OpprettSøknadBehandlingISakTask(
    private val journalpostClient: JournalpostClient,
    private val kontantstøtteOppgaveMapper: KontantstøtteOppgaveMapper,
    private val barnetrygdOppgaveMapper: BarnetrygdOppgaveMapper,
    private val ksSakClient: KsSakClient,
    private val baSakClient: BaSakClient,
    private val featureToggleService: FeatureToggleService,
    private val taskService: TaskService,
    private val baksVersjonertSøknadClientService: BaksVersjonertSøknadClientService,
    private val adressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService,
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

                    val brukAutomatiskBehandlingÅrsak =
                        featureToggleService.isEnabled(FeatureToggle.BRUK_AUTOMATISK_BEHANDLING_ÅRSAK)

                    val søknad = baksVersjonertSøknadClientService.hentVersjonertBarnetrygdSøknad(journalpost.journalpostId)
                    val søknadManglerVedlegg = søknad.harDokumentasjonsbehovUtenVedlegg()

                    val skalSjekkeTidligereInnvilgetBarnetrygd = behandlingType == BehandlingType.FØRSTEGANGSBEHANDLING && brukAutomatiskBehandlingÅrsak

                    val søkerHarHattInnvilgetBarnetrygd =
                        if (skalSjekkeTidligereInnvilgetBarnetrygd) {
                            baSakClient.søkerHarHattInnvilgetBarnetrygd(fagsak.id)
                        } else {
                            false
                        }

                    val behandlingÅrsak =
                        utledBehandlingsårsak(
                            behandlingType = behandlingType,
                            brukAutomatiskBehandlingÅrsak = brukAutomatiskBehandlingÅrsak,
                            søkerHarHattInnvilgetBarnetrygd = søkerHarHattInnvilgetBarnetrygd,
                            søknadManglerVedlegg = søknadManglerVedlegg,
                            søkerEllerBarnHarStrengtFortroligAdresse = {
                                adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)
                            },
                        )

                    val kategori = barnetrygdOppgaveMapper.utledBehandlingKategoriFraSøknad(søknad)
                    val underkategori = barnetrygdOppgaveMapper.utledBehandlingUnderkategoriFraSøknad(journalpost)
                    val brevkode = journalpost.dokumenter?.firstOrNull { it.brevkode != null }?.brevkode
                    val fagsakErLøpende = fagsak.status == FagsakStatus.LØPENDE

                    log.info("Oppretter ny $kategori $behandlingType behandling med årsak $behandlingÅrsak i ba-sak")

                    val årsakerTilManuellBehandling =
                        utledStatistikkFeilårsaker(
                            fagsakErLøpende = fagsakErLøpende,
                            søkerHarHattInnvilgetBarnetrygd = søkerHarHattInnvilgetBarnetrygd,
                            søknadManglerVedlegg = søknadManglerVedlegg,
                        )

                    if (årsakerTilManuellBehandling.isNotEmpty() && brukAutomatiskBehandlingÅrsak) {
                        val statistikkDto =
                            AutovedtakSøknadStatistikkDto(
                                journalpostId = journalpost.journalpostId.toLong(),
                                feilårsaker = årsakerTilManuellBehandling,
                            )
                        taskService.save(
                            Task(
                                type = SendAutovedtakSøknadstatistikkTilBaSakTask.TASK_STEP_TYPE,
                                payload = jsonMapper.writeValueAsString(statistikkDto),
                            ),
                        )
                    }

                    baSakClient.opprettBehandling(
                        kategori = kategori,
                        underkategori = underkategori,
                        behandlingÅrsak = behandlingÅrsak,
                        søkersIdent = brukersIdent,
                        søknadMottattDato = journalpost.datoMottatt ?: LocalDateTime.now(),
                        behandlingType = behandlingType,
                        fagsakId = fagsakId.toLong(),
                        søknadsinfo = Søknadsinfo(journalpostId = journalpost.journalpostId, brevkode = brevkode, erDigital = journalpost.erDigitalKanal()),
                    )
                }
            }

            else -> {
                throw IllegalStateException("$tema ikke støttet")
            }
        }
    }

    private fun utledBehandlingsårsak(
        behandlingType: BehandlingType,
        brukAutomatiskBehandlingÅrsak: Boolean,
        søkerHarHattInnvilgetBarnetrygd: Boolean,
        søknadManglerVedlegg: Boolean,
        søkerEllerBarnHarStrengtFortroligAdresse: () -> Boolean,
    ): BehandlingÅrsak =
        if (behandlingType == BehandlingType.FØRSTEGANGSBEHANDLING &&
            brukAutomatiskBehandlingÅrsak &&
            !søkerHarHattInnvilgetBarnetrygd &&
            !søknadManglerVedlegg &&
            !søkerEllerBarnHarStrengtFortroligAdresse()
        ) {
            BehandlingÅrsak.AUTOMATISK_BEHANDLING_AV_SØKNAD
        } else {
            BehandlingÅrsak.SØKNAD
        }

    private fun utledStatistikkFeilårsaker(
        fagsakErLøpende: Boolean,
        søkerHarHattInnvilgetBarnetrygd: Boolean,
        søknadManglerVedlegg: Boolean,
    ): List<AutovedtakSøknadFeilårsak> =
        buildList {
            if (fagsakErLøpende) add(AutovedtakSøknadFeilårsak.SØKER_HAR_LØPENDE_BARNETRYGD)
            if (!fagsakErLøpende && søkerHarHattInnvilgetBarnetrygd) add(AutovedtakSøknadFeilårsak.SØKER_HAR_HATT_BARNETRYGD_TIDLIGERE)
            if (søknadManglerVedlegg) add(AutovedtakSøknadFeilårsak.SØKNAD_MANGLER_VEDLEGG)
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
