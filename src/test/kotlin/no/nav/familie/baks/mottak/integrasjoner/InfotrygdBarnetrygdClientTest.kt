package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.familie.baks.mottak.AbstractWiremockTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@ActiveProfiles("dev", "mock-oauth")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InfotrygdBarnetrygdClientTest : AbstractWiremockTest() {
    @Autowired
    lateinit var infotrygdClient: InfotrygdBarnetrygdClient

    @Test
    @Tag("integration")
    fun `hentSaker skal returnere SakDto`() {
        stubFor(
            post(urlEqualTo("/infotrygd/barnetrygd/saker"))
                .withRequestBody(
                    equalToJson(
                        "{\n" +
                            "  \"brukere\": [\"20086600000\"],\n" +
                            "  \"barn\": [\"31038600000\"]\n" +
                            "}",
                    ),
                ).willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(gyldigSakResponse()),
                ),
        )

        val sakDto = infotrygdClient.hentSaker(brukersIdenter, barnasIdenter).bruker.first()
    }

    @Test
    @Tag("integration")
    fun `hentLøpendeUtbetalinger skal returnere StønadDto`() {
        stubFor(
            post(urlEqualTo("/infotrygd/barnetrygd/stonad"))
                .withRequestBody(
                    equalToJson(
                        "{\n" +
                            "  \"brukere\": [\"20086600000\"],\n" +
                            "  \"barn\": [\"31038600000\"]\n" +
                            "}",
                    ),
                ).willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(gyldigStønadResponse()),
                ),
        )

        val stønadDto = infotrygdClient.hentLøpendeUtbetalinger(brukersIdenter, barnasIdenter).bruker.first()
    }

    private fun gyldigStønadResponse(): String =
        Files.readString(
            ClassPathResource("testdata/hentInfotrygdstønad-response.json").file.toPath(),
            StandardCharsets.UTF_8,
        )

    private fun gyldigSakResponse(): String =
        Files.readString(
            ClassPathResource("testdata/hentInfotrygdsaker-response.json").file.toPath(),
            StandardCharsets.UTF_8,
        )

    companion object {
        private val brukersIdenter = listOf("20086600000")
        private val barnasIdenter = listOf("31038600000")
    }
}
