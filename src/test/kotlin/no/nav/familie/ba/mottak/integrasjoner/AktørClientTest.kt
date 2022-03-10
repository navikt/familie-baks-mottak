package no.nav.familie.ba.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import no.nav.familie.ba.mottak.DevLauncher
import no.nav.familie.kontrakter.felles.Ressurs.Companion.success
import no.nav.familie.kontrakter.felles.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles


@SpringBootTest(classes = [DevLauncher::class], properties = ["FAMILIE_INTEGRASJONER_API_URL=http://localhost:28085/api"])
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktørClientTest {

    @Autowired
    @Qualifier("aktørClient")
    lateinit var aktørClient: AktørClient

    @Test
    @Tag("integration")
    fun `hentAktør returnerer OK`() {
        stubFor(get(urlEqualTo("/api/aktoer/v1"))
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(objectMapper.writeValueAsString(success(mapOf("aktørId" to 1L))))))

        val aktørId = aktørClient.hentAktørId("12")
        assertThat(aktørId).isEqualTo("1")

        verify(getRequestedFor(urlEqualTo("/api/aktoer/v1"))
                       .withHeader("Nav-Personident", equalTo("12")))
    }

    @Test
    @Tag("integration")
    fun `hentPersonident returnerer OK`() {
        stubFor(get(urlEqualTo("/api/aktoer/v1/fraaktorid"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(success(mapOf("personIdent" to 1L))))))

        val personIdent = aktørClient.hentPersonident("12")
        assertThat(personIdent).isEqualTo("1")

        verify(getRequestedFor(urlEqualTo("/api/aktoer/v1/fraaktorid"))
            .withHeader("Nav-Aktorid", equalTo("12")))
    }

}