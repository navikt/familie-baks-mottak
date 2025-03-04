package no.nav.familie.baks.mottak.integrasjoner

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.søknad.SøknadTestData
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.kontrakter.felles.Behandlingstema
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

class BarnetrygdOppgaveMapperTest {

    private val enhetsnummerService: EnhetsnummerService = mockk()
    private val pdlClient: PdlClient = mockk()
    private val søknadRepository: SøknadRepository = mockk()
    private val unleashService: UnleashNextMedContextService = mockk()

    private val barnetrygdOppgaveMapper = BarnetrygdOppgaveMapper(
        enhetsnummerService = enhetsnummerService,
        pdlClient = pdlClient,
        søknadRepository = søknadRepository,
        unleashService = unleashService
    )

    @BeforeEach
    fun oppsett() {
        every { unleashService.isEnabled(FeatureToggleConfig.SETT_BEHANDLINGSTEMA_OG_BEHANDLINGSTYPE_FOR_KLAGE, false) } returns true
    }

    @Nested
    inner class HentBehandlingstema {
        @Test
        fun `skal returnere ordinær barnetrygd hvis man har dokumenter for både barnetrygd søknad og klage, og er digital`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    kanal = "NAV_NO",
                    dokumenter = listOf(
                        DokumentInfo(
                            dokumentInfoId = "321",
                            brevkode = Brevkoder.BARNETRYGD_ORDINÆR_SØKNAD,
                        ),
                        DokumentInfo(
                            dokumentInfoId = "132",
                            brevkode = Brevkoder.KLAGE,
                        ),
                    )
                )

            // Act
            val behandlingstema = barnetrygdOppgaveMapper.hentBehandlingstema(journalpost)

            // Assert
            assertThat(behandlingstema).isEqualTo(Behandlingstema.OrdinærBarnetrygd)
        }

        @Test
        fun `skal returnere null for klage`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    dokumenter = listOf(
                        DokumentInfo(
                            dokumentInfoId = "321",
                            brevkode = Brevkoder.KLAGE,
                        ),
                    )
                )

            // Act
            val behandlingstema = barnetrygdOppgaveMapper.hentBehandlingstema(journalpost)

            // Assert
            assertThat(behandlingstema).isNull()
        }

        @Test
        fun `skal returnere ordinær barnetrygd for klage hvis toggle er skrudd av`() {
            // Arrange
            every { unleashService.isEnabled(FeatureToggleConfig.SETT_BEHANDLINGSTEMA_OG_BEHANDLINGSTYPE_FOR_KLAGE, false) } returns false

            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    dokumenter = listOf(
                        DokumentInfo(
                            dokumentInfoId = "321",
                            brevkode = Brevkoder.KLAGE,
                        ),
                    )
                )

            // Act
            val behandlingstema = barnetrygdOppgaveMapper.hentBehandlingstema(journalpost)

            // Assert
            assertThat(behandlingstema).isEqualTo(Behandlingstema.OrdinærBarnetrygd)
        }
    }

    @Nested
    inner class HentBehandlingstype {
        @Test
        fun `skal returnere EØS hvis man har dokumenter for både barnetrygd EØS søknad og klage, og er digital`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    kanal = "NAV_NO",
                    dokumenter = listOf(
                        DokumentInfo(
                            dokumentInfoId = "321",
                            brevkode = Brevkoder.BARNETRYGD_ORDINÆR_SØKNAD,
                        ),
                        DokumentInfo(
                            dokumentInfoId = "132",
                            brevkode = Brevkoder.KLAGE,
                        ),
                    )
                )

            every { søknadRepository.getByJournalpostId(journalpost.journalpostId) } returns DBBarnetrygdSøknad(
                id = 0,
                objectMapper.writeValueAsString(SøknadTestData.barnetrygdSøknad()),
                "12345678093",
                LocalDateTime.now(),
            )

            // Act
            val behandlingstype = barnetrygdOppgaveMapper.hentBehandlingstype(journalpost)

            // Assert
            assertThat(behandlingstype).isEqualTo(Behandlingstype.EØS)
        }

        @Test
        fun `skal returnere behandlingstype klage hvis man har dokumenter for klage`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    dokumenter = listOf(
                        DokumentInfo(
                            dokumentInfoId = "321",
                            brevkode = Brevkoder.KLAGE,
                        ),
                    )
                )

            // Act
            val behandlingstype = barnetrygdOppgaveMapper.hentBehandlingstype(journalpost)

            // Assert
            assertThat(behandlingstype).isEqualTo(Behandlingstype.Klage)
        }

        @Test
        fun `skal returnere behandlingstype null hvis man har dokumenter for klage men toggle er skrudd av`() {
            // Arrange
            every { unleashService.isEnabled(FeatureToggleConfig.SETT_BEHANDLINGSTEMA_OG_BEHANDLINGSTYPE_FOR_KLAGE, false) } returns false

            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    dokumenter = listOf(
                        DokumentInfo(
                            dokumentInfoId = "321",
                            brevkode = Brevkoder.KLAGE,
                        ),
                    )
                )

            // Act
            val behandlingstype = barnetrygdOppgaveMapper.hentBehandlingstype(journalpost)

            // Assert
            assertThat(behandlingstype).isNull()
        }
    }
}