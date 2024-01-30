package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.familie.baks.mottak.DevLauncher
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@SpringBootTest(classes = [DevLauncher::class], properties = ["FAMILIE_KS_INFOTRYGD_API_URL=http://localhost:28085"])
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InfotrygdKontantstøtteClientTest {
    @Autowired
    lateinit var infotrygdKontantstøtteClient: InfotrygdKontantstøtteClient

    @Test
    @Tag("integration")
    fun `hentSaker skal returnere SakDto`() {
        stubFor(
            post(urlEqualTo("/api/hentPerioderMedKontantstotteIInfotrygdByBarn"))
                .withRequestBody(
                    equalToJson(
                        "{\n" +
                            "  \"barn\": [\"31038600000\"]\n" +
                            "}",
                    ),
                )
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(gyldigStønadResponse()),
                ),
        )

        val sakDto = infotrygdKontantstøtteClient.hentPerioderMedKontantstotteIInfotrygdByBarn(barnasIdenter)
    }

    private fun gyldigStønadResponse(): String {
        return Files.readString(
            ClassPathResource("testdata/hentInfotrygdKontantstøtteStønad-response.json").file.toPath(),
            StandardCharsets.UTF_8,
        )
    }


    companion object {
        private val brukersIdenter = listOf("20086600000")
        private val barnasIdenter = listOf("31038600000")
    }
}
