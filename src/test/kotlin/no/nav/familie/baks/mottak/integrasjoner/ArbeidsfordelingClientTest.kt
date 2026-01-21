package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.familie.baks.mottak.AbstractWiremockTest
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggle.HENT_ARBEIDSFORDELING_MED_BEHANDLINGSTYPE
import no.nav.familie.baks.mottak.fake.FakeFeatureToggleService
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "mock-oauth")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArbeidsfordelingClientTest : AbstractWiremockTest() {
    @Autowired
    lateinit var arbeidsfordelingClient: ArbeidsfordelingClient

    @Autowired
    lateinit var featureToggleService: FakeFeatureToggleService

    @Test
    @Tag("integration")
    fun `hentBehandlendeEnheterPåIdent skal returnere enhet på person uten behandlingstype-parameter`() {
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
                behandlingstype = null,
            )
        assertThat(response.single().enhetId).isEqualTo("4863")
        assertThat(response.single().enhetNavn).isEqualTo("NAV Familie- og pensjonsytelser midlertidig enhet")
    }

    @Test
    @Tag("integration")
    fun `hentBehandlendeEnheterPåIdent skal returnere enhet på person med behandlingstype-parameter`() {
        featureToggleService.set(HENT_ARBEIDSFORDELING_MED_BEHANDLINGSTYPE, true)

        stubFor(
            WireMock.post(urlEqualTo("/api/arbeidsfordeling/enhet/KON?behandlingstype=E%C3%98S")).withRequestBody(WireMock.equalToJson("""{"ident":"123"}""")).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(
                    gyldigEnhetResponse(),
                ),
            ),
        )

        val response =
            arbeidsfordelingClient.hentBehandlendeEnheterPåIdent(
                personIdent = "123",
                tema = Tema.KON,
                behandlingstype = Behandlingstype.EØS,
            )
        assertThat(response.single().enhetId).isEqualTo("4863")
        assertThat(response.single().enhetNavn).isEqualTo("NAV Familie- og pensjonsytelser midlertidig enhet")
    }

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
            arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = "123",
                tema = Tema.KON,
                behandlingstype = null,
            )
        assertThat(response.enhetId).isEqualTo("4863")
        assertThat(response.enhetNavn).isEqualTo("NAV Familie- og pensjonsytelser midlertidig enhet")
    }

    @Test
    @Tag("integration")
    fun `hentBehandlendeEnheterPåIdent skal ikke returnere enhet på person`() {
        stubFor(
            WireMock.post(urlEqualTo("/api/arbeidsfordeling/enhet/KON")).withRequestBody(WireMock.equalToJson("""{"ident":"123"}""")).willReturn(
                aResponse().withStatus(500).withBody(
                    objectMapper.writeValueAsString(Ressurs.failure<String>("test")),
                ),
            ),
        )

        val response =
            assertThrows<IntegrasjonException> {
                arbeidsfordelingClient.hentBehandlendeEnheterPåIdent(
                    personIdent = "123",
                    tema = Tema.KON,
                    behandlingstype = null,
                )
            }
        assertThat(response.message).isEqualTo("Feil ved henting av behandlende enheter på ident m/ tema ${Tema.KON} og behandlingstype null")
    }

    @Test
    @Tag("integration")
    fun `hentBehandlendeEnhetPåIdent skal ikke returnere enhet på person`() {
        stubFor(
            WireMock.post(urlEqualTo("/api/arbeidsfordeling/enhet/KON")).withRequestBody(WireMock.equalToJson("""{"ident":"123"}""")).willReturn(
                aResponse().withStatus(500).withBody(
                    objectMapper.writeValueAsString(Ressurs.failure<String>("test")),
                ),
            ),
        )

        val response =
            assertThrows<IntegrasjonException> {
                arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                    personIdent = "123",
                    tema = Tema.KON,
                    behandlingstype = null,
                )
            }
        assertThat(response.message).isEqualTo("Feil ved henting av behandlende enheter på ident m/ tema ${Tema.KON} og behandlingstype null")
    }

    @Test
    @Tag("integration")
    fun `hentBehandlendeEnhetPåIdent skal kaste feil ved returnere flere enhet på person`() {
        stubFor(
            WireMock.post(urlEqualTo("/api/arbeidsfordeling/enhet/KON")).withRequestBody(WireMock.equalToJson("""{"ident":"123"}""")).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(
                    flereGyldigEnhetResponse(),
                ),
            ),
        )

        val response =
            assertThrows<IllegalStateException> {
                arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                    personIdent = "123",
                    tema = Tema.KON,
                    behandlingstype = null,
                )
            }
        assertThat(response.message).isEqualTo("Forventet bare 1 enhet på ident men fantes flere")
    }

    private fun gyldigEnhetResponse() =
        """
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

    private fun flereGyldigEnhetResponse() =
        """
{
  "data": [
    {
      "enhetId": "4863",
      "enhetNavn": "NAV Familie- og pensjonsytelser midlertidig enhet"
    },
    {
      "enhetId": "5432",
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
