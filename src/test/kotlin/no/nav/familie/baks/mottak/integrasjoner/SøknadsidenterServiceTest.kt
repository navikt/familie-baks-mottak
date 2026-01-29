package no.nav.familie.baks.mottak.integrasjoner

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.søknad.SøknadTestData
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadTestData
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV9
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV6
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SøknadsidenterServiceTest {
    private val baksVersjonertSøknadClient: BaksVersjonertSøknadClientService = mockk()
    private val søknadsidenterService: SøknadsidenterService = SøknadsidenterService(baksVersjonertSøknadClient)

    @Nested
    inner class HentIdenterFraSøknad {
        @Test
        fun `hentIdenterFraBarnetrygdSøknad returnerer liste med identer fra klient`() {
            // Arrange
            val journalpostId = "1"
            val søkerIdent = "123"
            val førsteBarnIdent = "456"
            val andreBarneIdent = "789"

            val versjonertSøknad =
                VersjonertBarnetrygdSøknadV9(
                    barnetrygdSøknad =
                        SøknadTestData.barnetrygdSøknad(
                            søker = SøknadTestData.lagSøker(søkerIdent),
                            barn =
                                listOf(
                                    SøknadTestData.lagBarn(førsteBarnIdent),
                                    SøknadTestData.lagBarn(andreBarneIdent),
                                ),
                        ),
                )

            every {
                baksVersjonertSøknadClient.hentVersjonertBarnetrygdSøknad(journalpostId)
            } returns versjonertSøknad

            // Act
            val identer = søknadsidenterService.hentIdenterFraBarnetrygdSøknad(journalpostId)

            // Assert
            val expectedIdenter = listOf(søkerIdent, førsteBarnIdent, andreBarneIdent)
            assertThat(identer).isEqualTo(expectedIdenter)
        }

        @Test
        fun `hentIdenterFraKontantstøtteSøknad returnerer liste med identer fra klient`() {
            // Arrange
            val journalpostId = "1"
            val søkerIdent = "123"
            val førsteBarnIdent = "456"
            val andreBarneIdent = "789"

            val kontantstøtteSøknad =
                VersjonertKontantstøtteSøknadV6(
                    kontantstøtteSøknad =
                        KontantstøtteSøknadTestData.kontantstøtteSøknad(
                            søker = KontantstøtteSøknadTestData.lagSøker(søkerIdent),
                            barn =
                                listOf(
                                    KontantstøtteSøknadTestData.lagBarn(førsteBarnIdent),
                                    KontantstøtteSøknadTestData.lagBarn(andreBarneIdent),
                                ),
                        ),
                )

            every {
                baksVersjonertSøknadClient.hentVersjonertKontantstøtteSøknad(journalpostId)
            } returns kontantstøtteSøknad

            // Act
            val identer = søknadsidenterService.hentIdenterFraKontantstøtteSøknad(journalpostId)

            // Assert
            val expectedIdenter = listOf(søkerIdent, førsteBarnIdent, andreBarneIdent)
            assertThat(identer).isEqualTo(expectedIdenter)
        }
    }
}
