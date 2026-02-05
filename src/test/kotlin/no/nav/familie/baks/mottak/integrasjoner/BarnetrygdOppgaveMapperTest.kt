package no.nav.familie.baks.mottak.integrasjoner

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.søknad.SøknadTestData
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Brevkoder
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.DokumentInfo
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class BarnetrygdOppgaveMapperTest {
    private val enhetsnummerService: EnhetsnummerService = mockk()
    private val pdlClient: PdlClientService = mockk()
    private val søknadRepository: SøknadRepository = mockk()

    private val barnetrygdOppgaveMapper =
        BarnetrygdOppgaveMapper(
            enhetsnummerService = enhetsnummerService,
            pdlClientService = pdlClient,
            søknadRepository = søknadRepository,
        )

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
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = "321",
                                brevkode = Brevkoder.BARNETRYGD_ORDINÆR_SØKNAD,
                            ),
                            DokumentInfo(
                                dokumentInfoId = "132",
                                brevkode = Brevkoder.KLAGE,
                            ),
                        ),
                )

            // Act
            val behandlingstema = barnetrygdOppgaveMapper.hentBehandlingstema(journalpost)

            // Assert
            assertThat(behandlingstema).isEqualTo(Behandlingstema.OrdinærBarnetrygd)
        }

        @Test
        fun `skal returnere barnetrygd eøs for journalposter med ba ordinær søknad dokument og som har dnummer men ikke er digital`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    bruker = Bruker("41018512345", type = BrukerIdType.FNR),
                    kanal = null,
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = "321",
                                brevkode = Brevkoder.BARNETRYGD_ORDINÆR_SØKNAD,
                            ),
                        ),
                )

            // Act
            val behandlingstema = barnetrygdOppgaveMapper.hentBehandlingstema(journalpost)

            // Assert
            assertThat(behandlingstema).isEqualTo(Behandlingstema.BarnetrygdEØS)
        }

        @Test
        fun `skal returnere null for journalpost med klagedokument og med dnummer`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    bruker = Bruker(id = "41018512345", type = BrukerIdType.FNR),
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = "321",
                                brevkode = Brevkoder.KLAGE,
                            ),
                        ),
                )

            // Act
            val behandlingstema = barnetrygdOppgaveMapper.hentBehandlingstema(journalpost)

            // Assert
            assertThat(behandlingstema).isNull()
        }

        @Test
        fun `skal returnere null for journalpost med klagedokument`() {
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
            val behandlingstema = barnetrygdOppgaveMapper.hentBehandlingstema(journalpost)

            // Assert
            assertThat(behandlingstema).isNull()
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
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = "321",
                                brevkode = Brevkoder.BARNETRYGD_ORDINÆR_SØKNAD,
                            ),
                            DokumentInfo(
                                dokumentInfoId = "132",
                                brevkode = Brevkoder.KLAGE,
                            ),
                        ),
                )

            every { søknadRepository.getByJournalpostId(journalpost.journalpostId) } returns
                DBBarnetrygdSøknad(
                    id = 0,
                    søknadJson = jsonMapper.writeValueAsString(SøknadTestData.barnetrygdSøknad()),
                    fnr = "12345678093",
                    opprettetTid = LocalDateTime.now(),
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
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = "321",
                                brevkode = Brevkoder.KLAGE,
                            ),
                        ),
                )

            // Act
            val behandlingstype = barnetrygdOppgaveMapper.hentBehandlingstype(journalpost)

            // Assert
            assertThat(behandlingstype).isEqualTo(Behandlingstype.Klage)
        }
    }
}
