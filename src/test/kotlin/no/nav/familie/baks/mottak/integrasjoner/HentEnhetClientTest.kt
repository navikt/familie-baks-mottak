package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.familie.baks.mottak.AbstractWiremockTest
import no.nav.familie.kontrakter.felles.jsonMapper
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "mock-oauth")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HentEnhetClientTest : AbstractWiremockTest() {
    @Autowired
    lateinit var client: HentEnhetClientService

    @Test
    @Tag("integration")
    fun `hentEnhet skal returnere enhet`() {
        stubFor(
            get(urlEqualTo("/norg2/api/v1/enhet/1234"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(Enhet("1234", "enhetNavn", true, "Aktiv"))),
                ),
        )

        val response = client.hentEnhet("1234")
        assertThat(response.enhetId).isEqualTo("1234")
        assertThat(response.oppgavebehandler).isTrue()

        client.hentEnhet("1234")
    }

    @Test
    @Tag("integration")
    fun `hentEnhet skal returnere feil`() {
        stubFor(
            get(urlEqualTo("/norg2/api/v1/enhet/4321"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withBody("feilmelding"),
                ),
        )

        Assertions
            .assertThatThrownBy { client.hentEnhet("4321") }
            .hasMessageContaining("Henting av enhet med id 4321 feilet. 500 INTERNAL_SERVER_ERROR feilmelding")
            .isInstanceOf(java.lang.IllegalStateException::class.java)
    }

    @Test
    @Tag("integration")
    fun `enhet skal caches`() {
        stubFor(
            get(urlEqualTo("/norg2/api/v1/enhet/1111"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(Enhet("1234", "gammeltNavn", true, "Aktiv"))),
                ),
        )

        assertThat(client.hentEnhet("1111").navn).isEqualTo("gammeltNavn")

        stubFor(
            get(urlEqualTo("/norg2/api/v1/enhet/1111"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(Enhet("1234", "Nytt navn", true, "Aktiv"))),
                ),
        )

        assertThat(client.hentEnhet("1111").navn).isEqualTo("gammeltNavn")
    }
}
