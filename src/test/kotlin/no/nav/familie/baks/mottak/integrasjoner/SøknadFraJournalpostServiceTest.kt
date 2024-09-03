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
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.kontrakter.felles.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SøknadFraJournalpostServiceTest {
    private val mockedBarnetrygdSøknadService: BarnetrygdSøknadService = mockk()
    private val mockedKontantstøtteSøknadService: KontantstøtteSøknadService = mockk()
    private val søknadFraJournalpostService: SøknadFraJournalpostService =
        SøknadFraJournalpostService(
            mockedBarnetrygdSøknadService,
            mockedKontantstøtteSøknadService,
        )

    @Nested
    inner class HentBarnasIdenterForKontantstøtteTest {
        @Test
        fun `skal hente identer for barna`() {
            // Arrange
            val journalpostId = "1"

            val kontantstøtteSøknad =
                kontantstøtteSøknad(
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
                    fnr = "123",
                    journalpostId = journalpostId,
                )

            every {
                mockedKontantstøtteSøknadService.hentDBKontantstøtteSøknadForJournalpost(journalpostId)
            } returns dbKontantstøtteSøknad

            // Act
            val identer = søknadFraJournalpostService.hentBarnasIdenterForKontantstøtte(journalpostId)

            // Assert
            assertThat(identer).contains("815", "493", "00")
        }
    }

    @Nested
    inner class HentBarnasIdenterForBarnetrygdTest {
        @Test
        fun `skal hente identer for barna`() {
            // Arrange
            val journalpostId = "1"

            val kontantstøtteSøknad =
                barnetrygdSøknad(
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
                    fnr = "123",
                    journalpostId = journalpostId,
                )

            every {
                mockedBarnetrygdSøknadService.hentDBSøknadFraJournalpost(journalpostId)
            } returns dbBarnetrygdSøknad

            // Act
            val identer = søknadFraJournalpostService.hentBarnasIdenterForBarnetrygd(journalpostId)

            // Assert
            assertThat(identer).contains("815", "493", "00")
        }
    }
}
