package no.nav.familie.ba.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.*
import no.nav.familie.ba.mottak.config.ApplicationConfig
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.objectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles


@SpringBootTest(classes = [ApplicationConfig::class], properties = ["FAMILIE_INTEGRASJONER_API_URL=http://localhost:28085/api"])
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DokarkivClientTest {

    @Autowired
    lateinit var dokarkivClient: DokarkivClient


    @Test
    @Tag("integration")
    fun `oppdaterJournalpost skal kjøre OK`() {
        val journalpostId = "12345678"
        stubFor(put(urlEqualTo("/api/arkiv/v2/$journalpostId"))
            .withRequestBody(equalToJson(forventetRequestJson()))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(response)))

        dokarkivClient.oppdaterJournalpostSak("12345678", "11111111", "12345678910")

    }

    @Test
    @Tag("integration")
    fun `ferdigstillJournalpost skal kjøre OK`() {
        val journalpostId = "12345678"
        stubFor(put(urlEqualTo("/api/arkiv/v2/$journalpostId/ferdigstill?journalfoerendeEnhet=9999"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(response)))

        dokarkivClient.ferdigstillJournalpost("12345678")

    }

    @Test
    @Tag("integration")
    fun `DokarkivClient skal kaste feil hvis response er ugyldig`() {
        val journalpostId = "12345678"
        stubFor(put(urlEqualTo("/api/arkiv/v2/$journalpostId/ferdigstill?journalfoerendeEnhet=9999"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody(objectMapper.writeValueAsString(Ressurs.failure<String>("test")))))

        Assertions.assertThatThrownBy {
            dokarkivClient.ferdigstillJournalpost("12345678")
        }.isInstanceOf(IntegrasjonException::class.java)
            .hasMessageContaining("status=500 body={\"data\":null,\"status\":\"FEILET\",\"melding\":\"test\",\"stacktrace\":")
    }

    fun forventetRequestJson(): String {
        return "{ \n" +
            "  \"bruker\" : {\n" +
            "    \"idType\" : \"FNR\",\n" +
            "    \"id\" : \"12345678910\"\n" +
            "  },\n" +
            "  \"tema\" : \"BAR\",\n" +
            "  \"sak\" : {\n" +
            "    \"fagsakId\" : \"11111111\",\n" +
            "    \"fagsaksystem\" : \"BA\"\n" +
            "  }\n" +
            "}"
    }

    companion object {
        private val response = objectMapper.writeValueAsString(Ressurs.success(mapOf("journalpostId" to "12345678"),"test"))
    }
}
