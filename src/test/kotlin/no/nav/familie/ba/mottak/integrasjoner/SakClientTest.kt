package no.nav.familie.ba.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.*
import no.nav.familie.ba.mottak.config.ApplicationConfig
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import java.io.IOException


@SpringBootTest(classes = [ApplicationConfig::class], properties = ["FAMILIE_BA_SAK_API_URL=http://localhost:28085/api"])
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SakClientTest {

    @Autowired
    lateinit var sakClient: SakClient

    @Test
    @Tag("integration")
    fun `hentSaksnummer skal returnere fagsakId`() {
        stubFor(post(urlEqualTo("/api/fagsaker"))
            .withRequestBody(equalToJson("{ \"personIdent\": \"$personIdent\" }"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(gyldigResponse())))

        val response = sakClient.hentSaksnummer(personIdent)
        Assertions.assertThat(response).isEqualTo(fagsakId.toString())
    }

    @Throws(IOException::class) private fun gyldigResponse(): String {
        return "{\n" +
            "    \"data\": {\n" +
            "        \"opprettetTidspunkt\": \"2020-03-19T10:36:21.678775\",\n" +
            "        \"id\": $fagsakId,\n" +
            "        \"søkerFødselsnummer\": \"12345678910\",\n" +
            "        \"status\": \"OPPRETTET\",\n" +
            "        \"behandlinger\": []\n" +
            "    },\n" +
            "    \"status\": \"SUKSESS\",\n" +
            "    \"melding\": \"Innhenting av data var vellykket\",\n" +
            "    \"stacktrace\": null\n" +
            "}"
    }

    companion object {
        private val personIdent = "12345678910"
        private val fagsakId = 1L
    }
}