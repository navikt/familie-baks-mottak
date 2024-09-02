package no.nav.familie.baks.mottak.integrasjoner

import io.mockk.every
import io.mockk.mockk
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

class SøknadFraJournalpostServiceTest {

    private val mockedBarnetrygdSøknadService: BarnetrygdSøknadService = mockk()
    private val mockedKontantstøtteSøknadService: KontantstøtteSøknadService = mockk()
    private val søknadFraJournalpostService: SøknadFraJournalpostService = SøknadFraJournalpostService(
        mockedBarnetrygdSøknadService,
        mockedKontantstøtteSøknadService
    )

    @Nested
    inner class HentIdenterForKontantstøtteTest {

        @Test
        fun `skal hente identer for søker med barn`() {

            // Arrange
            val journalpostId = "1"
            val fødselsnummer = "123"

            val kontantstøtteSøknad = kontantstøtteSøknad(
                søker = lagSøker(
                    fnr = fødselsnummer
                ),
                barn = lagBarn(
                    listOf(
                        "815",
                        "493",
                        "00"
                    )
                )
            )

            val dbKontantstøtteSøknad = DBKontantstøtteSøknad(
                søknadJson = objectMapper.writeValueAsString(kontantstøtteSøknad),
                fnr = fødselsnummer,
                journalpostId = journalpostId
            )

            every {
                mockedKontantstøtteSøknadService.hentDBKontantstøtteSøknadForJournalpost(journalpostId)
            } returns dbKontantstøtteSøknad

            // Act
            val identer = søknadFraJournalpostService.hentIdenterForKontantstøtte(journalpostId)

            // Assert
            assertThat(identer).contains(
                fødselsnummer,
                "815",
                "493",
                "00"
            )

        }


    }

    @Nested
    inner class HentIdenterForBarnetrygdTest {

        @Test
        fun `skal hente identer for søker med barn`() {

            // Arrange
            val journalpostId = "1"
            val fødselsnummer = "123"

            val kontantstøtteSøknad = barnetrygdSøknad(
                søker = lagSøker(
                    fnr = fødselsnummer
                ),
                barn = lagBarn(
                    listOf(
                        "815",
                        "493",
                        "00"
                    )
                )
            )

            val dbBarnetrygdSøknad = DBBarnetrygdSøknad(
                søknadJson = objectMapper.writeValueAsString(kontantstøtteSøknad),
                fnr = fødselsnummer,
                journalpostId = journalpostId
            )

            every {
                mockedBarnetrygdSøknadService.hentDBSøknadFraJournalpost(journalpostId)
            } returns dbBarnetrygdSøknad

            // Act
            val identer = søknadFraJournalpostService.hentIdenterForBarnetrygd(journalpostId)

            // Assert
            assertThat(identer).contains(
                fødselsnummer,
                "815",
                "493",
                "00"
            )

        }


    }

}