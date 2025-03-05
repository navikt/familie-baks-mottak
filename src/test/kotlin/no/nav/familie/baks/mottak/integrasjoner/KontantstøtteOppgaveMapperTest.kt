package no.nav.familie.baks.mottak.integrasjoner

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadTestData
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadRepository
import no.nav.familie.kontrakter.felles.Brevkoder
import no.nav.familie.kontrakter.felles.journalpost.DokumentInfo
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class KontantstøtteOppgaveMapperTest {
    private val enhetsnummerService: EnhetsnummerService = mockk()
    private val pdlClient: PdlClient = mockk()
    private val kontantstøtteSøknadRepository: KontantstøtteSøknadRepository = mockk()
    private val unleashService: UnleashNextMedContextService = mockk()

    private val kontantstøtteOppgaveMapper =
        KontantstøtteOppgaveMapper(
            enhetsnummerService = enhetsnummerService,
            pdlClient = pdlClient,
            kontantstøtteSøknadRepository = kontantstøtteSøknadRepository,
            unleashService = unleashService,
        )

    @BeforeEach
    fun oppsett() {
        every { unleashService.isEnabled(FeatureToggleConfig.SETT_BEHANDLINGSTEMA_OG_BEHANDLINGSTYPE_FOR_KLAGE, false) } returns true
    }

    @Nested
    inner class HentBehandlingstemaTest {
        @Test
        fun `skal returnere null for behandlingstema    `() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                )

            // Act
            val behandlingstema = kontantstøtteOppgaveMapper.hentBehandlingstema(journalpost)

            // Assert
            assertThat(behandlingstema).isNull()
        }
    }

    @Nested
    inner class HentBehandlingstypeTest {
        @Test
        fun `skal returnere NASJON hvis man har dokumenter for både kontantstøtte NASJONAL søknad og klage, og er digital`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    kanal = "NAV_NO",
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = "321",
                                brevkode = Brevkoder.KONTANTSTØTTE_SØKNAD,
                            ),
                            DokumentInfo(
                                dokumentInfoId = "132",
                                brevkode = Brevkoder.KLAGE,
                            ),
                        ),
                )

            every { kontantstøtteSøknadRepository.getByJournalpostId(journalpost.journalpostId) } returns
                DBKontantstøtteSøknad(
                    id = 0,
                    objectMapper.writeValueAsString(KontantstøtteSøknadTestData.kontantstøtteSøknad()),
                    "12345678093",
                    LocalDateTime.now(),
                )

            // Act
            val behandlingstype = kontantstøtteOppgaveMapper.hentBehandlingstype(journalpost)

            // Assert
            assertThat(behandlingstype).isEqualTo(Behandlingstype.NASJONAL)
        }

        @Test
        fun `skal returnere behandlingstype klage hvis man har dokumenter for klage`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = "321",
                                brevkode = Brevkoder.KLAGE,
                            ),
                        ),
                )

            // Act
            val behandlingstype = kontantstøtteOppgaveMapper.hentBehandlingstype(journalpost)

            // Assert
            assertThat(behandlingstype).isEqualTo(Behandlingstype.Klage)
        }

        @Test
        fun `skal returnere behandlingstype NASJONAL hvis man har dokumenter for klage men toggle er skrudd av`() {
            // Arrange
            every { unleashService.isEnabled(FeatureToggleConfig.SETT_BEHANDLINGSTEMA_OG_BEHANDLINGSTYPE_FOR_KLAGE, false) } returns false

            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = "321",
                                brevkode = Brevkoder.KLAGE,
                            ),
                        ),
                )

            // Act
            val behandlingstype = kontantstøtteOppgaveMapper.hentBehandlingstype(journalpost)

            // Assert
            assertThat(behandlingstype).isEqualTo(Behandlingstype.NASJONAL)
        }
    }
}
