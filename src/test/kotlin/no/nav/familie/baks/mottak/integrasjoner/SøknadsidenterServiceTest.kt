package no.nav.familie.baks.mottak.integrasjoner

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.søknad.SøknadTestData
import no.nav.familie.baks.mottak.søknad.SøknadTestData.barnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadTestData.kontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadTestData.lagBarn
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadTestData.lagSøker
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class SøknadsidenterServiceTest {
    private val mockedBarnetrygdSøknadService: BarnetrygdSøknadService = mockk()
    private val mockedKontantstøtteSøknadService: KontantstøtteSøknadService = mockk()
    private val søknadsidenterService: SøknadsidenterService =
        SøknadsidenterService(
            mockedBarnetrygdSøknadService,
            mockedKontantstøtteSøknadService,
        )

    private val søkersFødselsnummer: String = "123"

    @Nested
    inner class HentIdenterForKontantstøtteViaJournalpostTest {
        @Test
        fun `skal hente identer for barna`() {
            // Arrange
            val journalpostId = "1"

            val kontantstøtteSøknad =
                kontantstøtteSøknad(
                    søker =
                        lagSøker(
                            søkersFødselsnummer,
                        ),
                    barn =
                        listOf(
                            lagBarn("815"),
                            lagBarn("493"),
                            lagBarn("00"),
                        ),
                )

            val dbKontantstøtteSøknad =
                DBKontantstøtteSøknad(
                    søknadJson = objectMapper.writeValueAsString(kontantstøtteSøknad),
                    fnr = søkersFødselsnummer,
                    journalpostId = journalpostId,
                )

            every {
                mockedKontantstøtteSøknadService.hentDBKontantstøtteSøknadForJournalpost(journalpostId)
            } returns dbKontantstøtteSøknad

            // Act
            val identer = søknadsidenterService.hentIdenterForKontantstøtteViaJournalpost(journalpostId)

            // Assert
            assertThat(identer.first).isEqualTo(søkersFødselsnummer)
            assertThat(identer.second).isEqualTo(listOf("815", "493", "00"))
        }
    }

    @Nested
    inner class HentIdenterForBarnetrygdViaJournalpostTest {
        @Test
        fun `skal hente identer for barna`() {
            // Arrange
            val journalpostId = "1"

            val kontantstøtteSøknad =
                barnetrygdSøknad(
                    søker =
                        SøknadTestData.lagSøker(
                            søkersFødselsnummer,
                        ),
                    barn =
                        listOf(
                            SøknadTestData.lagBarn("815"),
                            SøknadTestData.lagBarn("493"),
                            SøknadTestData.lagBarn("00"),
                        ),
                )

            val dbBarnetrygdSøknad =
                DBBarnetrygdSøknad(
                    søknadJson = objectMapper.writeValueAsString(kontantstøtteSøknad),
                    fnr = søkersFødselsnummer,
                    journalpostId = journalpostId,
                )

            every {
                mockedBarnetrygdSøknadService.hentDBSøknadFraJournalpost(journalpostId)
            } returns dbBarnetrygdSøknad

            // Act
            val identer = søknadsidenterService.hentIdenterForBarnetrygdViaJournalpost(journalpostId)

            // Assert
            assertThat(identer.first).isEqualTo(søkersFødselsnummer)
            assertThat(identer.second).isEqualTo(listOf("815", "493", "00"))
        }
    }

    @Nested
    inner class HentIdenterIDigitalSøknadFraJournalpost {
        @Test
        fun `skal hente identer i digital søknad om barnetrygd fra journalpostId`() {
            // Arrange
            val journalpostId = "1"

            val barnetrygdSøknad =
                barnetrygdSøknad(
                    søker =
                        SøknadTestData.lagSøker(
                            søkersFødselsnummer,
                        ),
                    barn =
                        listOf(
                            SøknadTestData.lagBarn("815"),
                            SøknadTestData.lagBarn("493"),
                            SøknadTestData.lagBarn("00"),
                        ),
                )

            val dbBarnetrygdSøknad =
                DBBarnetrygdSøknad(
                    søknadJson = objectMapper.writeValueAsString(barnetrygdSøknad),
                    fnr = søkersFødselsnummer,
                    journalpostId = journalpostId,
                )

            every {
                mockedBarnetrygdSøknadService.hentDBSøknadFraJournalpost(journalpostId)
            } returns dbBarnetrygdSøknad

            // Act
            val identerIDigitalSøknad = søknadsidenterService.hentIdenterIDigitalSøknadFraJournalpost(tema = Tema.BAR, journalpostId = journalpostId)

            // Assert
            assertThat(identerIDigitalSøknad).isEqualTo(listOf(søkersFødselsnummer, "815", "493", "00"))
        }

        @Test
        fun `skal hente identer i digital søknad om kontantstøtte fra journalpostId`() {
            // Arrange
            val journalpostId = "1"

            val kontantstøtteSøknad =
                kontantstøtteSøknad(
                    søker =
                        lagSøker(
                            søkersFødselsnummer,
                        ),
                    barn =
                        listOf(
                            lagBarn("815"),
                            lagBarn("493"),
                            lagBarn("00"),
                        ),
                )

            val dbKontantstøtteSøknad =
                DBKontantstøtteSøknad(
                    søknadJson = objectMapper.writeValueAsString(kontantstøtteSøknad),
                    fnr = søkersFødselsnummer,
                    journalpostId = journalpostId,
                )

            every {
                mockedKontantstøtteSøknadService.hentDBKontantstøtteSøknadForJournalpost(journalpostId)
            } returns dbKontantstøtteSøknad

            // Act
            val identerIDigitalSøknad = søknadsidenterService.hentIdenterIDigitalSøknadFraJournalpost(tema = Tema.KON, journalpostId = journalpostId)

            // Assert
            assertThat(identerIDigitalSøknad).isEqualTo(listOf(søkersFødselsnummer, "815", "493", "00"))
        }
    }

    @ParameterizedTest
    @EnumSource(value = Tema::class, names = ["BAR", "KON"], mode = EnumSource.Mode.EXCLUDE)
    fun `skal kaste feil dersom tema ikke er BAR eller KON`(tema: Tema) {
        // Act & Assert
        val error = assertThrows<Error> { søknadsidenterService.hentIdenterIDigitalSøknadFraJournalpost(tema = tema, journalpostId = "1") }
        assertThat(error.message).isEqualTo("Kan ikke hente identer i digital søknad for tema $tema")
    }
}
