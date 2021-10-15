package no.nav.familie.ba.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.*
import no.nav.familie.ba.mottak.DevLauncher
import no.nav.familie.kontrakter.felles.objectMapper
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles


@SpringBootTest(classes = [DevLauncher::class], properties = ["NORG2_API_URL=http://localhost:28085/norg2/"])
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HentEnhetClientTest {

    @Autowired
    lateinit var client: HentEnhetClient

    @Test
    @Tag("integration")
    fun `hentEnhet skal returnere enhet`() {
        stubFor(get(urlEqualTo("/norg2/api/v1/enhet/1234"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(objectMapper.writeValueAsString(Enhet("1234", "enhetNavn", true, "Aktiv")))))

        val response = client.hentEnhet("1234")
        assertThat(response.enhetId).isEqualTo("1234")
        assertThat(response.oppgavebehandler).isTrue()

        client.hentEnhet("1234")
    }

    @Test
    @Tag("integration")
    fun `hentEnhet skal returnere feil`() {
        stubFor(get(urlEqualTo("/norg2/api/v1/enhet/4321"))
                        .willReturn(aResponse()
                                            .withStatus(500)
                                            .withBody("feilmelding")))

        Assertions.assertThatThrownBy { client.hentEnhet("4321") }
                .hasMessageContaining("Henting av enhet med id 4321 feilet. 500 feilmelding")
                .isInstanceOf(java.lang.IllegalStateException::class.java)
    }

    @Test
    @Tag("integration")
    fun `enhet skal caches`() {
        stubFor(get(urlEqualTo("/norg2/api/v1/enhet/1111"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(objectMapper.writeValueAsString(Enhet("1234", "gammeltNavn", true,"Aktiv")))))

        assertThat(client.hentEnhet("1111").navn).isEqualTo("gammeltNavn")

        stubFor(get(urlEqualTo("/norg2/api/v1/enhet/1111"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(objectMapper.writeValueAsString(Enhet("1234", "Nytt navn", true, "Aktiv")))))

        assertThat(client.hentEnhet("1111").navn).isEqualTo("gammeltNavn")

    }
}