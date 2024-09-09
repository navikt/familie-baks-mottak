package no.nav.familie.baks.mottak.søknad.kontantstøtte

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadRepository
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteVedleggRepository
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KontantstøtteSøknadServiceTest {
    private val mockKontantstøtteSøknadRepository: KontantstøtteSøknadRepository = mockk()
    private val mockKontantstøtteVedleggRepository: KontantstøtteVedleggRepository = mockk()
    private val mockTaskService: TaskService = mockk()
    private val mockVedleggClient: FamilieDokumentClient = mockk()

    private val kontantstøtteSøknadService: KontantstøtteSøknadService =
        KontantstøtteSøknadService(
            kontantstøtteSøknadRepository = mockKontantstøtteSøknadRepository,
            kontantstøtteVedleggRepository = mockKontantstøtteVedleggRepository,
            taskService = mockTaskService,
            vedleggClient = mockVedleggClient,
        )

    @Nested
    inner class FinnDBKontantstøtteSøknadForJournalpostTest {
        @Test
        fun `skal finne søknad`() {
            // Arrange
            val journalpostId = "1"

            val søknad =
                DBKontantstøtteSøknad(
                    søknadJson = "",
                    fnr = "123",
                )

            every {
                mockKontantstøtteSøknadRepository.hentSøknadForJournalpost(journalpostId)
            } returns søknad

            // Act
            val resultat =
                kontantstøtteSøknadService.finnDBKontantstøtteSøknadForJournalpost(
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
                mockKontantstøtteSøknadRepository.hentSøknadForJournalpost(journalpostId)
            } returns null

            // Act
            val resultat =
                kontantstøtteSøknadService.finnDBKontantstøtteSøknadForJournalpost(
                    journalpostId = journalpostId,
                )

            // assert
            assertThat(resultat).isNull()
        }
    }

    @Nested
    inner class HentDBKontantstøtteSøknadForJournalpostTest {
        @Test
        fun `skal hente søknad`() {
            // Arrange
            val journalpostId = "1"

            val søknad =
                DBKontantstøtteSøknad(
                    søknadJson = "",
                    fnr = "123",
                )

            every {
                mockKontantstøtteSøknadRepository.hentSøknadForJournalpost(journalpostId)
            } returns søknad

            // Act
            val resultat =
                kontantstøtteSøknadService.hentDBKontantstøtteSøknadForJournalpost(
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
                mockKontantstøtteSøknadRepository.hentSøknadForJournalpost(journalpostId)
            } returns null

            // Act & assert
            val exception =
                assertThrows<IllegalStateException> {
                    kontantstøtteSøknadService.hentDBKontantstøtteSøknadForJournalpost(
                        journalpostId = journalpostId,
                    )
                }
            assertThat(exception.message).isEqualTo("Fant ikke søknad for journalpost $journalpostId")
        }
    }
}
