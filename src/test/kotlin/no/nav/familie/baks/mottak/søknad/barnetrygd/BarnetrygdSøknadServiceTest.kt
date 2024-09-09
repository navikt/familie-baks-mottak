package no.nav.familie.baks.mottak.søknad.barnetrygd

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadVedleggRepository
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BarnetrygdSøknadServiceTest {
    private val mockSøknadRepository: SøknadRepository = mockk()
    private val mockVedleggRepository: SøknadVedleggRepository = mockk()
    private val mockTaskService: TaskService = mockk()
    private val mockVedleggClient: FamilieDokumentClient = mockk()

    private val barnetrygdSøknadService: BarnetrygdSøknadService =
        BarnetrygdSøknadService(
            søknadRepository = mockSøknadRepository,
            vedleggRepository = mockVedleggRepository,
            taskService = mockTaskService,
            vedleggClient = mockVedleggClient,
        )

    @Nested
    inner class FinnDBSøknadFraJournalpostTest {
        @Test
        fun `skal finne søknad`() {
            // Arrange
            val journalpostId = "1"

            val søknad =
                DBBarnetrygdSøknad(
                    søknadJson = "",
                    fnr = "123",
                )

            every {
                mockSøknadRepository.finnDBSøknadForJournalpost(journalpostId)
            } returns søknad

            // Act
            val resultat =
                barnetrygdSøknadService.finnDBSøknadFraJournalpost(
                    journalpostId = journalpostId,
                )

            // assert
            assertThat(resultat).isEqualTo(søknad)
        }

        @Test
        fun `skal ikke finne søknad om den ikke eksiterer i repoet`() {
            // Arrange
            val journalpostId = "1"

            every {
                mockSøknadRepository.finnDBSøknadForJournalpost(journalpostId)
            } returns null

            // Act
            val resultat =
                barnetrygdSøknadService.finnDBSøknadFraJournalpost(
                    journalpostId = journalpostId,
                )

            // assert
            assertThat(resultat).isNull()
        }
    }

    @Nested
    inner class HentDBSøknadFraJournalpostTest {
        @Test
        fun `skal hente søknad`() {
            // Arrange
            val journalpostId = "1"

            val søknad =
                DBBarnetrygdSøknad(
                    søknadJson = "",
                    fnr = "123",
                )

            every {
                mockSøknadRepository.finnDBSøknadForJournalpost(journalpostId)
            } returns søknad

            // Act
            val resultat =
                barnetrygdSøknadService.hentDBSøknadFraJournalpost(
                    journalpostId = journalpostId,
                )

            // assert
            assertThat(resultat).isEqualTo(søknad)
        }

        @Test
        fun `skal kaste exception om søknaden ikke eksiterer i repoet`() {
            // Arrange
            val journalpostId = "1"

            every {
                mockSøknadRepository.finnDBSøknadForJournalpost(journalpostId)
            } returns null

            // Act & assert
            val exception =
                assertThrows<IllegalStateException> {
                    barnetrygdSøknadService.hentDBSøknadFraJournalpost(
                        journalpostId = journalpostId,
                    )
                }
            assertThat(exception.message).isEqualTo("Fant ikke søknad for journalpost $journalpostId")
        }
    }
}
