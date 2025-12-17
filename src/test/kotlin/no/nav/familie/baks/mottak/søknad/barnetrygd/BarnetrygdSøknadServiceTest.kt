package no.nav.familie.baks.mottak.søknad.barnetrygd

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBVedlegg
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadVedleggRepository
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.collections.mapOf

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
    inner class LagreDBSøknadTest {
        @Test
        fun `skal lagre søknad`() {
            // Arrange

            val søknad =
                DBBarnetrygdSøknad(
                    søknadJson = "",
                    fnr = "123",
                )

            every {
                mockSøknadRepository.save(søknad)
            } returns søknad

            // Act
            val resultat =
                barnetrygdSøknadService.lagreDBSøknad(
                    dbBarnetrygdSøknad = søknad,
                )

            // Assert
            assertThat(resultat).isEqualTo(søknad)
        }
    }

    @Nested
    inner class HentDBSøknadTest {
        @Test
        fun `skal hente søknad`() {
            // Arrange

            val søknad =
                DBBarnetrygdSøknad(
                    søknadJson = "",
                    fnr = "123",
                )

            every {
                mockSøknadRepository.hentDBSøknad(søknad.id)
            } returns søknad

            // Act
            val resultat =
                barnetrygdSøknadService.hentDBSøknad(
                    søknadId = søknad.id,
                )

            // Assert
            assertThat(resultat).isEqualTo(søknad)
        }

        @Test
        fun `skal kaste exception om søknaden ikke eksiterer i repoet`() {
            // Arrange
            val søknadId = 1L

            every {
                mockSøknadRepository.hentDBSøknad(søknadId)
            } returns null

            // Act
            val result =
                barnetrygdSøknadService.hentDBSøknad(
                    søknadId,
                )

            // Assert
            assertThat(result).isEqualTo(null)
        }
    }

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
        fun `skal ikke finne søknad om den ikke eksisterer i repoet`() {
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

    @Nested
    inner class HentLagredeVedleggTest {
        @Test
        fun `skal hente lagrede vedlegg`() {
            // Arrange
            val søknad =
                DBBarnetrygdSøknad(
                    søknadJson = "",
                    fnr = "123",
                )

            val vedlegg =
                DBVedlegg(
                    dokumentId = "1",
                    søknadId = 1,
                    data = ByteArray(0),
                )

            every {
                mockVedleggRepository.hentAlleVedlegg(søknad.id)
            } returns listOf(vedlegg)

            // Act
            val resultat =
                barnetrygdSøknadService.hentLagredeVedlegg(
                    søknad,
                )

            // assert
            assertThat(resultat).isEqualTo(mapOf(vedlegg.dokumentId to vedlegg))
        }

        @Test
        fun `skal ikke returnere noe om ingen vedlegg i repoet`() {
            // Arrange
            val søknad =
                DBBarnetrygdSøknad(
                    søknadJson = "",
                    fnr = "123",
                )

            every {
                mockVedleggRepository.hentAlleVedlegg(søknad.id)
            } returns emptyList()

            // Act
            val resultat =
                barnetrygdSøknadService.hentLagredeVedlegg(
                    søknad,
                )

            // assert
            assertThat(resultat).isEqualTo(emptyMap<String, DBVedlegg>())
        }
    }

    @Nested
    inner class SlettLagredeVedleggTest {
        @Test
        fun `skal slette lagrede vedlegg i repoet`() {
            // Arrange
            val søknad =
                DBBarnetrygdSøknad(
                    søknadJson = "",
                    fnr = "123",
                )

            every {
                mockVedleggRepository.slettAlleVedlegg(søknad.id)
            } returns Unit

            // Act
            val resultat =
                barnetrygdSøknadService.slettLagredeVedlegg(søknad)

            // assert
            assertThat(resultat).isEqualTo(Unit)
        }
    }
}
