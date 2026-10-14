package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import no.nav.familie.baks.mottak.AbstractWiremockTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@ActiveProfiles("dev", "mock-oauth")
class BaksVersjonertSøknadClientServiceTest : AbstractWiremockTest() {
    @Autowired
    lateinit var baksVersjonertSøknadClientService: BaksVersjonertSøknadClientService

    @Test
    @Tag("integration")
    fun `skal hente barnetrygdsøknad fra integrasjoner kun én gang for samme journalpost`() {
        // Arrange
        val journalpostId = "9001"
        val url = "/api/baks/versjonertsoknad/ba/$journalpostId"
        stubFor(get(urlEqualTo(url)).willReturn(okJson("versjonertBarnetrygdSøknadV9.json")))

        // Act
        val førsteSøknad = baksVersjonertSøknadClientService.hentVersjonertBarnetrygdSøknad(journalpostId)
        val andreSøknad = baksVersjonertSøknadClientService.hentVersjonertBarnetrygdSøknad(journalpostId)

        // Assert
        assertThat(andreSøknad).isEqualTo(førsteSøknad)
        verify(1, getRequestedFor(urlEqualTo(url)))
    }

    @Test
    @Tag("integration")
    fun `skal hente kontantstøttesøknad fra integrasjoner kun én gang for samme journalpost`() {
        // Arrange
        val journalpostId = "9002"
        val url = "/api/baks/versjonertsoknad/ks/$journalpostId"
        stubFor(get(urlEqualTo(url)).willReturn(okJson("versjonertKontantstøtteSøknadV6.json")))

        // Act
        val førsteSøknad = baksVersjonertSøknadClientService.hentVersjonertKontantstøtteSøknad(journalpostId)
        val andreSøknad = baksVersjonertSøknadClientService.hentVersjonertKontantstøtteSøknad(journalpostId)

        // Assert
        assertThat(andreSøknad).isEqualTo(førsteSøknad)
        verify(1, getRequestedFor(urlEqualTo(url)))
    }

    private fun okJson(filnavn: String) =
        aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(Files.readString(ClassPathResource("testdata/$filnavn").file.toPath(), StandardCharsets.UTF_8))
}
