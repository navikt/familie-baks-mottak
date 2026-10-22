package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggle
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleService
import no.nav.familie.baks.mottak.integrasjoner.AutovedtakSøknadFeilårsak
import no.nav.familie.baks.mottak.integrasjoner.AutovedtakSøknadStatistikkDto
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.BaksVersjonertSøknadClientService
import no.nav.familie.baks.mottak.integrasjoner.BarnetrygdOppgaveMapper
import no.nav.familie.baks.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.baks.mottak.integrasjoner.BehandlingType
import no.nav.familie.baks.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.baks.mottak.integrasjoner.BehandlingÅrsak
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.KontantstøtteOppgaveMapper
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.RestMinimalFagsak
import no.nav.familie.baks.mottak.søknad.SøknadTestData
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknad
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV9
import no.nav.familie.kontrakter.ba.søknad.v7.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OpprettSøknadBehandlingISakTaskTest {
    private val journalpostClient = mockk<JournalpostClient>()
    private val kontantstøtteOppgaveMapper = mockk<KontantstøtteOppgaveMapper>()
    private val barnetrygdOppgaveMapper = mockk<BarnetrygdOppgaveMapper>()
    private val ksSakClient = mockk<KsSakClient>()
    private val baSakClient = mockk<BaSakClient>()
    private val featureToggleService = mockk<FeatureToggleService>()
    private val baksVersjonertSøknadClientService = mockk<BaksVersjonertSøknadClientService>()
    private val taskService = mockk<TaskService>(relaxed = true)

    private val opprettSøknadBehandlingISakTask =
        OpprettSøknadBehandlingISakTask(
            journalpostClient = journalpostClient,
            kontantstøtteOppgaveMapper = kontantstøtteOppgaveMapper,
            barnetrygdOppgaveMapper = barnetrygdOppgaveMapper,
            ksSakClient = ksSakClient,
            baSakClient = baSakClient,
            featureToggleService = featureToggleService,
            baksVersjonertSøknadClientService = baksVersjonertSøknadClientService,
            taskService = taskService,
        )

    private val journalpostId = "1"
    private val fagsakId = 2L
    private val personIdent = "12345678910"

    private val task =
        Task(
            type = OpprettSøknadBehandlingISakTask.TASK_STEP_TYPE,
            payload = journalpostId,
            properties =
                java.util.Properties().apply {
                    this["journalpostId"] = journalpostId
                    this["fagsakId"] = fagsakId.toString()
                    this["personIdent"] = personIdent
                },
        )

    @BeforeEach
    fun setUp() {
        every { journalpostClient.hentJournalpost(journalpostId) } returns
            Journalpost(
                journalpostId = journalpostId,
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = Tema.BAR.name,
                bruker = null,
            )
        every { barnetrygdOppgaveMapper.utledBehandlingKategoriFraSøknad(any<VersjonertBarnetrygdSøknad>()) } returns BehandlingKategori.NASJONAL
        every { barnetrygdOppgaveMapper.utledBehandlingUnderkategoriFraSøknad(any()) } returns BehandlingUnderkategori.ORDINÆR
        justRun { baSakClient.opprettBehandling(any(), any(), any(), any(), any(), any(), any(), any()) }
        every { baSakClient.søkerHarHattInnvilgetBarnetrygd(fagsakId) } returns false
        every { baksVersjonertSøknadClientService.hentVersjonertBarnetrygdSøknad(journalpostId) } returns lagSøknad()
    }

    private fun lagSøknad(dokumentasjon: List<Søknaddokumentasjon> = listOf(SøknadTestData.lagAnnenDokumentasjon())) = VersjonertBarnetrygdSøknadV9(barnetrygdSøknad = SøknadTestData.barnetrygdSøknad(dokumentasjon = dokumentasjon))

    private fun lagFagsak(status: FagsakStatus) = RestMinimalFagsak(id = fagsakId, behandlinger = emptyList(), status = status)

    @Nested
    inner class UtledBehandlingsårsak {
        @Test
        fun `skal opprette automatisk førstegangsbehandling når fagsaken ikke er løpende`() {
            // Arrange
            every { baSakClient.hentMinimalRestFagsak(fagsakId) } returns lagFagsak(FagsakStatus.OPPRETTET)
            every { featureToggleService.isEnabled(FeatureToggle.BRUK_AUTOMATISK_BEHANDLING_ÅRSAK) } returns true

            // Act
            opprettSøknadBehandlingISakTask.doTask(task)

            // Assert
            verify(exactly = 1) {
                baSakClient.opprettBehandling(
                    kategori = any(),
                    underkategori = any(),
                    søkersIdent = any(),
                    behandlingÅrsak = BehandlingÅrsak.AUTOMATISK_BEHANDLING_AV_SØKNAD,
                    søknadMottattDato = any(),
                    behandlingType = BehandlingType.FØRSTEGANGSBEHANDLING,
                    fagsakId = any(),
                    søknadsinfo = any(),
                )
            }
            verify(exactly = 0) { taskService.save(any()) }
        }

        @Test
        fun `skal opprette manuell revurdering når fagsaken har løpende utbetaling`() {
            // Arrange
            every { baSakClient.hentMinimalRestFagsak(fagsakId) } returns lagFagsak(FagsakStatus.LØPENDE)
            every { featureToggleService.isEnabled(FeatureToggle.BRUK_AUTOMATISK_BEHANDLING_ÅRSAK) } returns true

            // Act
            opprettSøknadBehandlingISakTask.doTask(task)

            // Assert
            verify(exactly = 0) { baSakClient.søkerHarHattInnvilgetBarnetrygd(any()) }
            verify(exactly = 1) {
                baSakClient.opprettBehandling(
                    kategori = any(),
                    underkategori = any(),
                    søkersIdent = any(),
                    behandlingÅrsak = BehandlingÅrsak.SØKNAD,
                    søknadMottattDato = any(),
                    behandlingType = BehandlingType.REVURDERING,
                    fagsakId = any(),
                    søknadsinfo = any(),
                )
            }
            verifiserStatistikkTask(AutovedtakSøknadFeilårsak.SØKER_HAR_LØPENDE_BARNETRYGD)
        }

        @Test
        fun `skal opprette automatisk førstegangsbehandling når fagsaken er avsluttet`() {
            // Arrange
            every { baSakClient.hentMinimalRestFagsak(fagsakId) } returns lagFagsak(FagsakStatus.AVSLUTTET)
            every { featureToggleService.isEnabled(FeatureToggle.BRUK_AUTOMATISK_BEHANDLING_ÅRSAK) } returns true

            // Act
            opprettSøknadBehandlingISakTask.doTask(task)

            // Assert
            verify(exactly = 1) {
                baSakClient.opprettBehandling(
                    kategori = any(),
                    underkategori = any(),
                    søkersIdent = any(),
                    behandlingÅrsak = BehandlingÅrsak.AUTOMATISK_BEHANDLING_AV_SØKNAD,
                    søknadMottattDato = any(),
                    behandlingType = BehandlingType.FØRSTEGANGSBEHANDLING,
                    fagsakId = any(),
                    søknadsinfo = any(),
                )
            }
            verify(exactly = 0) { taskService.save(any()) }
        }

        @Test
        fun `skal opprette manuell førstegangsbehandling når søker har hatt utbetaling tidligere`() {
            // Arrange
            every { baSakClient.hentMinimalRestFagsak(fagsakId) } returns lagFagsak(FagsakStatus.AVSLUTTET)
            every { featureToggleService.isEnabled(FeatureToggle.BRUK_AUTOMATISK_BEHANDLING_ÅRSAK) } returns true
            every { baSakClient.søkerHarHattInnvilgetBarnetrygd(fagsakId) } returns true

            // Act
            opprettSøknadBehandlingISakTask.doTask(task)

            // Assert
            verify(exactly = 1) {
                baSakClient.opprettBehandling(
                    kategori = any(),
                    underkategori = any(),
                    søkersIdent = any(),
                    behandlingÅrsak = BehandlingÅrsak.SØKNAD,
                    søknadMottattDato = any(),
                    behandlingType = BehandlingType.FØRSTEGANGSBEHANDLING,
                    fagsakId = any(),
                    søknadsinfo = any(),
                )
            }
            verifiserStatistikkTask(AutovedtakSøknadFeilårsak.SØKER_HAR_HATT_BARNETRYGD_TIDLIGERE)
        }

        @Test
        fun `skal opprette manuell førstegangsbehandling når toggelen er av`() {
            // Arrange
            every { baSakClient.hentMinimalRestFagsak(fagsakId) } returns lagFagsak(FagsakStatus.OPPRETTET)
            every { featureToggleService.isEnabled(FeatureToggle.BRUK_AUTOMATISK_BEHANDLING_ÅRSAK) } returns false

            // Act
            opprettSøknadBehandlingISakTask.doTask(task)

            // Assert
            verify(exactly = 1) {
                baSakClient.opprettBehandling(
                    kategori = any(),
                    underkategori = any(),
                    søkersIdent = any(),
                    behandlingÅrsak = BehandlingÅrsak.SØKNAD,
                    søknadMottattDato = any(),
                    behandlingType = BehandlingType.FØRSTEGANGSBEHANDLING,
                    fagsakId = any(),
                    søknadsinfo = any(),
                )
            }
            verify(exactly = 0) { taskService.save(any()) }
        }

        @Test
        fun `skal opprette manuell førstegangsbehandling når søknaden har dokumentasjonsbehov uten vedlegg`() {
            // Arrange
            every { baSakClient.hentMinimalRestFagsak(fagsakId) } returns lagFagsak(FagsakStatus.OPPRETTET)
            every { featureToggleService.isEnabled(FeatureToggle.BRUK_AUTOMATISK_BEHANDLING_ÅRSAK) } returns true
            every { baksVersjonertSøknadClientService.hentVersjonertBarnetrygdSøknad(journalpostId) } returns
                lagSøknad(
                    dokumentasjon =
                        listOf(
                            Søknaddokumentasjon(
                                dokumentasjonsbehov = Dokumentasjonsbehov.AVTALE_DELT_BOSTED,
                                harSendtInn = false,
                                opplastedeVedlegg = emptyList(),
                                dokumentasjonSpråkTittel = mapOf("nb" to "Avtale om delt bosted"),
                            ),
                        ),
                )

            // Act
            opprettSøknadBehandlingISakTask.doTask(task)

            // Assert
            verify(exactly = 1) {
                baSakClient.opprettBehandling(
                    kategori = any(),
                    underkategori = any(),
                    søkersIdent = any(),
                    behandlingÅrsak = BehandlingÅrsak.SØKNAD,
                    søknadMottattDato = any(),
                    behandlingType = BehandlingType.FØRSTEGANGSBEHANDLING,
                    fagsakId = any(),
                    søknadsinfo = any(),
                )
            }
            verifiserStatistikkTask(AutovedtakSøknadFeilårsak.SØKNAD_MANGLER_VEDLEGG)
        }

        @Test
        fun `skal sende flere årsaker til manuell behandling`() {
            // Arrange
            every { baSakClient.hentMinimalRestFagsak(fagsakId) } returns lagFagsak(FagsakStatus.AVSLUTTET)
            every { featureToggleService.isEnabled(FeatureToggle.BRUK_AUTOMATISK_BEHANDLING_ÅRSAK) } returns true
            every { baSakClient.søkerHarHattInnvilgetBarnetrygd(fagsakId) } returns true
            every { baksVersjonertSøknadClientService.hentVersjonertBarnetrygdSøknad(journalpostId) } returns
                lagSøknad(
                    dokumentasjon =
                        listOf(
                            Søknaddokumentasjon(
                                dokumentasjonsbehov = Dokumentasjonsbehov.AVTALE_DELT_BOSTED,
                                harSendtInn = false,
                                opplastedeVedlegg = emptyList(),
                                dokumentasjonSpråkTittel = mapOf("nb" to "Avtale om delt bosted"),
                            ),
                        ),
                )

            // Act
            opprettSøknadBehandlingISakTask.doTask(task)

            // Assert
            verifiserStatistikkTask(
                AutovedtakSøknadFeilårsak.SØKER_HAR_HATT_BARNETRYGD_TIDLIGERE,
                AutovedtakSøknadFeilårsak.SØKNAD_MANGLER_VEDLEGG,
            )
        }
    }

    private fun verifiserStatistikkTask(vararg forventedeFeilårsaker: AutovedtakSøknadFeilårsak) {
        val taskSlot = slot<Task>()
        verify(exactly = 1) { taskService.save(capture(taskSlot)) }

        val statistikkDto = jsonMapper.readValue(taskSlot.captured.payload, AutovedtakSøknadStatistikkDto::class.java)
        assertThat(taskSlot.captured.type).isEqualTo(`SendAutovedtakSøknadstatistikkTilBaSakTask`.TASK_STEP_TYPE)
        assertThat(statistikkDto.journalpostId).isEqualTo(journalpostId.toLong())
        assertThat(statistikkDto.feilårsaker).containsExactlyInAnyOrder(*forventedeFeilårsaker)
    }
}
