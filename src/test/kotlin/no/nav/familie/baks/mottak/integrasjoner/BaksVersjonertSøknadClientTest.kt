package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.familie.baks.mottak.AbstractWiremockTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import no.nav.familie.kontrakter.ba.søknad.v9.BarnetrygdSøknad as VersjonertBarnetrygdSøknadV9
import no.nav.familie.kontrakter.ks.søknad.v6.KontantstøtteSøknad as VersjonertKontantstøtteSøknadV6

@ActiveProfiles("dev", "mock-oauth")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaksVersjonertSøknadClientTest : AbstractWiremockTest() {
    @Autowired
    lateinit var baksVersjonertSøknadClient: BaksVersjonertSøknadClient

    @Test
    @Tag("integration")
    fun `hentVersjonertBarnetrygdSøknad skal returnere VersjonertBarnetrygdSøknad OK og castes til sin tilhørende versjon`() {
        // Arrange
        val journalpostId = "123"
        stubFor(
            get(urlEqualTo("/api/baks/versjonertsoknad/ba/$journalpostId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(hentResponsFraFil("versjonertBarnetrygdSøknadV9.json")),
                ),
        )

        // Act
        val versjonertBarnetrygdSøknad = baksVersjonertSøknadClient.hentVersjonertBarnetrygdSøknad(journalpostId)

        // Assert
        assertThat(versjonertBarnetrygdSøknad).isNotNull
        assertThat(versjonertBarnetrygdSøknad.barnetrygdSøknad.kontraktVersjon).isEqualTo(9)
        assertThat(versjonertBarnetrygdSøknad.barnetrygdSøknad).isInstanceOf(VersjonertBarnetrygdSøknadV9::class.java)

        assertDoesNotThrow { versjonertBarnetrygdSøknad.barnetrygdSøknad as VersjonertBarnetrygdSøknadV9 }
    }

    @Test
    @Tag("integration")
    fun `hentVersjonertBarnetrygdSøknad skal kaste IntegrasjonException hvis respons er null`() {
        val journalpostId = "123"
        stubFor(
            get(urlEqualTo("/api/baks/versjonertsoknad/ba/$journalpostId"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody(hentResponsFraFil("feiletRespons.json")),
                ),
        )

        val value = assertThrows<IntegrasjonException> { baksVersjonertSøknadClient.hentVersjonertBarnetrygdSøknad(journalpostId) }
        assertThat(value.message).isEqualTo("Henting av søknad for barnetrygd feilet. journalpostId: $journalpostId")
    }

    @Test
    @Tag("integration")
    fun `hentVersjonertKontantStøtte skal returnere VersjonertKontantStøtte OK og castes til sin tilhørende versjon`() {
        // Arrange
        val journalpostId = "123"
        stubFor(
            get(urlEqualTo("/api/baks/versjonertsoknad/ks/$journalpostId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(hentResponsFraFil("versjonertKontantstøtteSøknadV6.json")),
                ),
        )

        // Act
        val versjonertKontantStøtte = baksVersjonertSøknadClient.hentVersjonertKontantstøtteSøknad(journalpostId)

        // Assert
        assertThat(versjonertKontantStøtte).isNotNull
        assertThat(versjonertKontantStøtte.kontantstøtteSøknad.kontraktVersjon).isEqualTo(5)
        assertThat(versjonertKontantStøtte.kontantstøtteSøknad).isInstanceOf(VersjonertKontantstøtteSøknadV6::class.java)

        assertDoesNotThrow { versjonertKontantStøtte.kontantstøtteSøknad as VersjonertKontantstøtteSøknadV6 }
    }

    @Test
    @Tag("integration")
    fun `hentVersjonertKontantStøtte skal kaste IntegrasjonException hvis respons er null`() {
        val journalpostId = "123"
        stubFor(
            get(urlEqualTo("/api/baks/versjonertsoknad/ks/$journalpostId"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody(hentResponsFraFil("feiletRespons.json")),
                ),
        )

        val value = assertThrows<IntegrasjonException> { baksVersjonertSøknadClient.hentVersjonertKontantstøtteSøknad(journalpostId) }
        assertThat(value.message).isEqualTo("Henting av søknad for kontantstøtte feilet. journalpostId: $journalpostId.")
    }

    @Throws(IOException::class)
    private fun hentResponsFraFil(filnavn: String): String =
        Files.readString(
            ClassPathResource("testdata/$filnavn").file.toPath(),
            StandardCharsets.UTF_8,
        )
}
