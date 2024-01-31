package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.familie.baks.mottak.DevLauncher
import org.assertj.core.api.Assertions.assertThat
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
import java.time.YearMonth
import kotlin.test.assertTrue

@SpringBootTest(classes = [DevLauncher::class], properties = ["FAMILIE_KS_INFOTRYGD_API_URL=http://localhost:28085"])
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InfotrygdKontantstøtteClientTest {
    @Autowired
    lateinit var infotrygdKontantstøtteClient: InfotrygdKontantstøtteClient

    @Test
    @Tag("integration")
    fun `hentPerioderMedKontantstotteIInfotrygdByBarn skal returnere liste med stønader for barnene`() {
        stubFor(
            post(urlEqualTo("/api/hentPerioderMedKontantstotteIInfotrygdByBarn"))
                .withRequestBody(
                    equalToJson(
                        "{\n" +
                            "  \"barn\": [\"barnFnr\"]\n" +
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
        assertThat(sakDto.data).hasSize(1)
        assertThat(sakDto.data.first().fom).isEqualTo(YearMonth.of(2023, 4))
        assertThat(sakDto.data.first().tom).isEqualTo(YearMonth.of(2023, 7))
        assertThat(sakDto.data.first().belop).isEqualTo(7500)
        assertThat(sakDto.data.first().fnr.asString).isEqualTo("søkerFnr")
        assertThat(sakDto.data.first().barn).hasSize(1).contains(BarnDto(fnr = Foedselsnummer("barnFnr")))
    }

    @Test
    @Tag("integration")
    fun `harKontantstøtteIInfotrygd skal true hvis en liste med barn har kontantstøtte`() {
        stubFor(
            post(urlEqualTo("/api/harKontantstøtteIInfotrygd"))
                .withRequestBody(
                    equalToJson(
                        "{\n" +
                            "  \"barn\": [\"barnFnr\"]\n" +
                            "}",
                    ),
                )
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true"),
                ),
        )

        assertTrue(infotrygdKontantstøtteClient.harKontantstøtteIInfotrygd(barnasIdenter))
    }

    private fun gyldigStønadResponse(): String {
        return Files.readString(
            ClassPathResource("testdata/hentInfotrygdKontantstøtteStønad-response.json").file.toPath(),
            StandardCharsets.UTF_8,
        )
    }

    companion object {
        private val barnasIdenter = listOf("barnFnr")
    }
}
