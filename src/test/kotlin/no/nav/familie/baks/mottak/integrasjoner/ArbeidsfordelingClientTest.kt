package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.familie.baks.mottak.DevLauncher
import no.nav.familie.kontrakter.felles.Tema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [DevLauncher::class], properties = ["FAMILIE_INTEGRASJONER_API_URL=http://localhost:28085/api"])
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArbeidsfordelingClientTest {
    @Autowired
    lateinit var arbeidsfordelingClient: ArbeidsfordelingClient

    @Test
    @Tag("integration")
    fun `hentBehandlendeEnhetPåIdent skal returnere enhet på person`() {
        stubFor(
            WireMock.post(urlEqualTo("/api/arbeidsfordeling/enhet/KON")).withRequestBody(WireMock.equalToJson("""{"ident":"123"}""")).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(
                    gyldigEnhetResponse(),
                ),
            ),
        )

        val response =
            arbeidsfordelingClient.hentBehandlendeEnheterPåIdent(
                personIdent = "123",
                tema = Tema.KON,
            )
        assertThat(response.single().enhetId).isEqualTo("4863")
        assertThat(response.single().enhetNavn).isEqualTo("NAV Familie- og pensjonsytelser midlertidig enhet")
    }

    private fun gyldigEnhetResponse() = """
{
  "data": [
    {
      "enhetId": "4863",
      "enhetNavn": "NAV Familie- og pensjonsytelser midlertidig enhet"
    }
  ],
  "status": "SUKSESS",
  "melding": "Innhenting av data var vellykket",
  "frontendFeilmelding": null,
  "stacktrace": null
}                              
        """
}
