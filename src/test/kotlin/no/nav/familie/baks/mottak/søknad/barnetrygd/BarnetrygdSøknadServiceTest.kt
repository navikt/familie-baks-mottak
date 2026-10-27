package no.nav.familie.baks.mottak.søknad.barnetrygd

import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClientService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBVedlegg
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadVedleggRepository
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BarnetrygdSøknadServiceTest {
    private val mockSøknadRepository: SøknadRepository = mockk()
    private val mockVedleggRepository: SøknadVedleggRepository = mockk()
    private val mockTaskService: TaskService = mockk()
    private val mockVedleggClient: FamilieDokumentClientService = mockk()

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
    inner class SlettDBSøknadOgVedleggTest {
        @Test
        fun `skal slette vedlegg og søknad i repoene`() {
            // Arrange
            val søknad =
                DBBarnetrygdSøknad(
                    søknadJson = "",
                    fnr = "123",
                )

            every { mockVedleggRepository.slettAlleVedlegg(søknad.id) } returns Unit
            every { mockSøknadRepository.slettSøknad(søknad.id) } returns Unit

            // Act
            barnetrygdSøknadService.slettDBSøknadOgVedlegg(søknad)

            // Assert
            verifyOrder {
                mockVedleggRepository.slettAlleVedlegg(søknad.id)
                mockSøknadRepository.slettSøknad(søknad.id)
            }
        }
    }
}
