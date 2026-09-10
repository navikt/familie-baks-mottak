package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggle
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleService
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
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
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.prosessering.domene.Task
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

    private val opprettSøknadBehandlingISakTask =
        OpprettSøknadBehandlingISakTask(
            journalpostClient = journalpostClient,
            kontantstøtteOppgaveMapper = kontantstøtteOppgaveMapper,
            barnetrygdOppgaveMapper = barnetrygdOppgaveMapper,
            ksSakClient = ksSakClient,
            baSakClient = baSakClient,
            featureToggleService = featureToggleService,
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
        every { barnetrygdOppgaveMapper.utledBehandlingKategoriFraSøknad(any()) } returns BehandlingKategori.NASJONAL
        every { barnetrygdOppgaveMapper.utledBehandlingUnderkategoriFraSøknad(any()) } returns BehandlingUnderkategori.ORDINÆR
        justRun { baSakClient.opprettBehandling(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

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
        }

        @Test
        fun `skal opprette manuell revurdering når fagsaken har løpende utbetaling`() {
            // Arrange
            every { baSakClient.hentMinimalRestFagsak(fagsakId) } returns lagFagsak(FagsakStatus.LØPENDE)
            every { featureToggleService.isEnabled(FeatureToggle.BRUK_AUTOMATISK_BEHANDLING_ÅRSAK) } returns true

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
                    behandlingType = BehandlingType.REVURDERING,
                    fagsakId = any(),
                    søknadsinfo = any(),
                )
            }
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
        }
    }
}
