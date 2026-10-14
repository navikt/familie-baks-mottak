package no.nav.familie.baks.mottak.integrasjoner

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadTestData
import no.nav.familie.kontrakter.felles.Brevkoder
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.DokumentInfo
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV6
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KontantstøtteOppgaveMapperTest {
    private val enhetsnummerService: EnhetsnummerService = mockk()
    private val pdlClient: PdlClientService = mockk()
    private val baksVersjonertSøknadClientService: BaksVersjonertSøknadClientService = mockk()

    private val kontantstøtteOppgaveMapper =
        KontantstøtteOppgaveMapper(
            enhetsnummerService = enhetsnummerService,
            pdlClientService = pdlClient,
            baksVersjonertSøknadClientService = baksVersjonertSøknadClientService,
        )

    @Nested
    inner class HentBehandlingstemaTest {
        @Test
        fun `skal returnere null for behandlingstema`() {
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

            every { baksVersjonertSøknadClientService.hentVersjonertKontantstøtteSøknad(journalpost.journalpostId) } returns
                VersjonertKontantstøtteSøknadV6(kontantstøtteSøknad = KontantstøtteSøknadTestData.kontantstøtteSøknad())

            // Act
            val behandlingstype = kontantstøtteOppgaveMapper.hentBehandlingstype(journalpost)

            // Assert
            assertThat(behandlingstype).isEqualTo(Behandlingstype.NASJONAL)
        }

        @Test
        fun `skal returnere behandlingstype eøs for digital ks søknad når søker har eøs-steg`() {
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
                        ),
                )

            every { baksVersjonertSøknadClientService.hentVersjonertKontantstøtteSøknad(journalpost.journalpostId) } returns
                VersjonertKontantstøtteSøknadV6(
                    kontantstøtteSøknad =
                        KontantstøtteSøknadTestData.kontantstøtteSøknad(
                            søker = KontantstøtteSøknadTestData.lagSøker().copy(harEøsSteg = true),
                        ),
                )

            // Act
            val behandlingstype = kontantstøtteOppgaveMapper.hentBehandlingstype(journalpost)

            // Assert
            assertThat(behandlingstype).isEqualTo(Behandlingstype.EØS)
        }

        @Test
        fun `skal returnere behandlingstype eøs for journalpost ks søknad med dnummer som ikke er digital`() {
            // Arrange
            val dnummer = "41018512345"

            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    bruker = Bruker(id = dnummer, type = BrukerIdType.FNR),
                    kanal = null,
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = "321",
                                brevkode = Brevkoder.KONTANTSTØTTE_SØKNAD,
                            ),
                        ),
                )

            // Act
            val behandlingstype = kontantstøtteOppgaveMapper.hentBehandlingstype(journalpost)

            // Assert
            assertThat(behandlingstype).isEqualTo(Behandlingstype.EØS)
            verify(exactly = 0) { baksVersjonertSøknadClientService.hentVersjonertKontantstøtteSøknad(any()) }
        }

        @Test
        fun `skal returnere behandlingstype klage hvis man har dokumenter for klage og journalpost inneholder dnummer`() {
            // Arrange
            val dnummer = "41018512345"

            val journalpost =
                Journalpost(
                    journalpostId = "123",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    bruker = Bruker(id = dnummer, type = BrukerIdType.FNR),
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
    }
}
