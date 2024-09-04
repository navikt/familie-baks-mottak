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
import no.nav.familie.kontrakter.felles.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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

        @Test
        fun `skal kaste exception om søknad er null`() {
            // Arrange
            val journalpostId = "1"

            every {
                mockedKontantstøtteSøknadService.hentDBKontantstøtteSøknadForJournalpost(journalpostId)
            } returns null

            // Act & assert
            val exception =
                assertThrows<IllegalStateException> {
                    søknadsidenterService.hentIdenterForKontantstøtteViaJournalpost(journalpostId)
                }
            assertThat(exception.message).isEqualTo("Fant ikke søknad for journalpost=$journalpostId")
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

        @Test
        fun `skal kaste exception om søknad er null`() {
            // Arrange
            val journalpostId = "1"

            every {
                mockedBarnetrygdSøknadService.hentDBSøknadFraJournalpost(journalpostId)
            } returns null

            // Act & assert
            val exception =
                assertThrows<IllegalStateException> {
                    søknadsidenterService.hentIdenterForBarnetrygdViaJournalpost(journalpostId)
                }
            assertThat(exception.message).isEqualTo("Fant ikke søknad for journalpost=$journalpostId")
        }
    }
}
