package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
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
import org.springframework.test.context.ActiveProfiles
import java.io.IOException
import java.time.LocalDate

@SpringBootTest(classes = [DevLauncher::class], properties = ["FAMILIE_BA_SAK_API_URL=http://localhost:28085/api"])
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaSakClientTest {
    @Autowired
    lateinit var baSakClient: BaSakClient

    @Test
    @Tag("integration")
    fun `hentSaksnummer skal returnere fagsakId`() {
        stubFor(
            post(urlEqualTo("/api/fagsaker"))
                .withRequestBody(equalToJson("{ \"personIdent\": \"$personIdent\" }"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(gyldigResponse()),
                ),
        )

        val response = baSakClient.hentFagsaknummerPåPersonident(personIdent)
        assertThat(response).isEqualTo(fagsakId)
    }

    @Test
    @Tag("integration")
    fun `skal hente minimal fagsak og mappe til RestMinimalFagsak`() {
        stubFor(
            get(urlEqualTo("/api/fagsaker/minimal/1"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(gyldigResponseMinimalSak()),
                ),
        )

        val response = baSakClient.hentMinimalRestFagsak(fagsakId)
        assertThat(response.id).isEqualTo(fagsakId)
        assertThat(response.behandlinger).hasSize(1)
        assertThat(response.behandlinger.first().aktiv).isTrue()
        assertThat(response.behandlinger.first().behandlingId).isEqualTo(1000)
        assertThat(response.behandlinger.first().kategori).isEqualTo(BehandlingKategori.NASJONAL)
        assertThat(response.behandlinger.first().underkategori).isEqualTo(BehandlingUnderkategori.ORDINÆR)
        assertThat(response.behandlinger.first().opprettetTidspunkt).isEqualTo(LocalDate.of(2023, 4, 2).atStartOfDay())
        assertThat(response.behandlinger.first().status).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(response.behandlinger.first().type).isEqualTo(BehandlingType.FØRSTEGANGSBEHANDLING)
        assertThat(response.behandlinger.first().vedtaksdato).isEqualTo(LocalDate.of(2023, 4, 3).atStartOfDay())
        assertThat(response.behandlinger.first().årsak).isEqualTo("SØKNAD")
        assertThat(response.behandlinger.first().resultat).isEqualTo("INNVILGET")
    }

    @Throws(IOException::class)
    private fun gyldigResponse(): String =
        "{\n" +
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

    @Throws(IOException::class)
    private fun gyldigResponseMinimalSak(): String =
        """
        {
          "data": {
            "opprettetTidspunkt": "2023-04-01T00:00:00.00",
            "id": $fagsakId,
            "søkerFødselsnummer": "42104200000",
            "status": "LØPENDE",
            "underBehandling": false,
            "løpendeKategori": "NASJONAL",
            "behandlinger": [
              {
                "behandlingId": 1000,
                "opprettetTidspunkt": "2023-04-02T00:00:00.00",
                "kategori": "NASJONAL",
                "underkategori": "ORDINÆR",
                "aktiv": true,
                "årsak": "SØKNAD",
                "type": "FØRSTEGANGSBEHANDLING",
                "status": "AVSLUTTET",
                "resultat": "INNVILGET",
                "vedtaksdato": "2023-04-03T00:00:00.00"
              }
            ],
            "tilbakekrevingsbehandlinger": [],
            "gjeldendeUtbetalingsperioder": []
          },
          "status": "SUKSESS",
          "melding": "Innhenting av data var vellykket",
          "frontendFeilmelding": null,
          "stacktrace": null
        }                                   
        """.trimIndent()

    companion object {
        private val personIdent = "12345678910"
        private val fagsakId = 1L
    }
}
