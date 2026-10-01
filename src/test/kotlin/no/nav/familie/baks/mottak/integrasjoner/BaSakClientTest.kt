package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.removeAllMappings
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import no.nav.familie.baks.mottak.AbstractWiremockTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

@ActiveProfiles("dev", "mock-oauth")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaSakClientTest : AbstractWiremockTest() {
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
        private val barnPersonIdent = "98765432100"
        private val skjermetBarnFagsakId = 42L
    }

    @Nested
    inner class HentFagsakForSkjermetBarn {
        @BeforeEach
        fun setUp() {
            removeAllMappings()
        }

        @Test
        @Tag("integration")
        fun `hentFagsakForSkjermetBarn skal returnere liste med løpende fagsak`() {
            stubFor(
                post(urlEqualTo("/api/fagsaker/hent-fagsaker-paa-person"))
                    .withRequestBody(
                        equalToJson("""{ "personIdent": "$barnPersonIdent", "fagsakTyper": ["SKJERMET_BARN"] }"""),
                    ).willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(gyldigResponseSkjermetBarnLøpende()),
                    ),
            )

            val response = baSakClient.hentFagsakForSkjermetBarn(barnPersonIdent)

            assertThat(response).hasSize(1)
            assertThat(response.first().id).isEqualTo(skjermetBarnFagsakId)
            assertThat(response.first().status).isEqualTo(FagsakStatus.LØPENDE)
        }

        @Test
        @Tag("integration")
        fun `hentFagsakForSkjermetBarn skal returnere tom liste når ingen fagsak finnes`() {
            stubFor(
                post(urlEqualTo("/api/fagsaker/hent-fagsaker-paa-person"))
                    .withRequestBody(
                        equalToJson("""{ "personIdent": "$barnPersonIdent", "fagsakTyper": ["SKJERMET_BARN"] }"""),
                    ).willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(tomResponseSkjermetBarn()),
                    ),
            )

            val response = baSakClient.hentFagsakForSkjermetBarn(barnPersonIdent)

            assertThat(response).isEmpty()
        }

        private fun gyldigResponseSkjermetBarnLøpende(): String =
            """
            {
              "data": [
                {
                  "id": $skjermetBarnFagsakId,
                  "status": "LØPENDE"
                }
              ],
              "status": "SUKSESS",
              "melding": "Innhenting av data var vellykket",
              "frontendFeilmelding": null,
              "stacktrace": null
            }
            """.trimIndent()

        private fun tomResponseSkjermetBarn(): String =
            """
            {
              "data": [],
              "status": "SUKSESS",
              "melding": "Innhenting av data var vellykket",
              "frontendFeilmelding": null,
              "stacktrace": null
            }
            """.trimIndent()
    }

    @Nested
    inner class OpprettBehandling {
        @BeforeEach
        fun setUp() {
            removeAllMappings()
        }

        @Test
        @Tag("integration")
        fun `skal kalle behandlinger-automatisk-soknad når behandlingÅrsak er AUTOMATISK_BEHANDLING_AV_SØKNAD`() {
            stubFor(
                post(urlEqualTo("/api/behandlinger/automatisk-soknad"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(gyldigOpprettBehandlingResponse()),
                    ),
            )

            baSakClient.opprettBehandling(
                kategori = BehandlingKategori.NASJONAL,
                underkategori = BehandlingUnderkategori.ORDINÆR,
                søkersIdent = personIdent,
                behandlingÅrsak = BehandlingÅrsak.AUTOMATISK_BEHANDLING_AV_SØKNAD,
                søknadMottattDato = LocalDateTime.of(2026, 1, 1, 0, 0),
                behandlingType = BehandlingType.FØRSTEGANGSBEHANDLING,
                fagsakId = fagsakId,
                søknadsinfo = Søknadsinfo(journalpostId = "12345", erDigital = true),
            )

            verify(postRequestedFor(urlEqualTo("/api/behandlinger/automatisk-soknad")))
        }

        @Test
        @Tag("integration")
        fun `opprettBehandling skal kalle behandlinger når behandlingÅrsak ikke er AUTOMATISK_BEHANDLING_AV_SØKNAD`() {
            stubFor(
                post(urlEqualTo("/api/behandlinger"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(gyldigOpprettBehandlingResponse()),
                    ),
            )

            baSakClient.opprettBehandling(
                kategori = BehandlingKategori.NASJONAL,
                underkategori = BehandlingUnderkategori.ORDINÆR,
                søkersIdent = personIdent,
                behandlingÅrsak = BehandlingÅrsak.SØKNAD,
                søknadMottattDato = LocalDateTime.of(2026, 1, 1, 0, 0),
                behandlingType = BehandlingType.FØRSTEGANGSBEHANDLING,
                fagsakId = fagsakId,
                søknadsinfo = Søknadsinfo(journalpostId = "12345", erDigital = true),
            )

            verify(postRequestedFor(urlEqualTo("/api/behandlinger")))
        }

        private fun gyldigOpprettBehandlingResponse(): String =
            """
            {
              "data": null,
              "status": "SUKSESS",
              "melding": "Innhenting av data var vellykket",
              "frontendFeilmelding": null,
              "stacktrace": null
            }
            """.trimIndent()
    }

    @Nested
    inner class SøkerHarHattInnvilgetBarnetrygd {
        private val url = "/api/barnetrygdhistorikk/fagsak/$fagsakId/soker-har-hatt-innvilget_barnetrygd"

        @BeforeEach
        fun setUp() {
            removeAllMappings()
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        @Tag("integration")
        fun `skal returnere svaret fra ba-sak`(harHattUtbetaling: Boolean) {
            stubFor(
                get(urlEqualTo(url))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(gyldigResponse(harHattUtbetaling)),
                    ),
            )

            val response = baSakClient.søkerHarHattInnvilgetBarnetrygd(fagsakId)

            assertThat(response).isEqualTo(harHattUtbetaling)
            verify(getRequestedFor(urlEqualTo(url)))
        }

        @Test
        @Tag("integration")
        fun `skal kaste IntegrasjonException når ba-sak returnerer respons uten data`() {
            stubFor(
                get(urlEqualTo(url))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseUtenData()),
                    ),
            )

            val exception = assertThrows<IntegrasjonException> { baSakClient.søkerHarHattInnvilgetBarnetrygd(fagsakId) }

            assertThat(exception.message).isEqualTo("Noe gikk galt")
        }

        @Test
        @Tag("integration")
        fun `skal kaste IntegrasjonException når kallet til ba-sak feiler`() {
            stubFor(
                get(urlEqualTo(url))
                    .willReturn(aResponse().withStatus(500)),
            )

            val exception = assertThrows<IntegrasjonException> { baSakClient.søkerHarHattInnvilgetBarnetrygd(fagsakId) }

            assertThat(exception.message).isEqualTo("Feil ved sjekk av om søker har hatt utbetaling i ba-sak.")
        }

        private fun gyldigResponse(harHattUtbetaling: Boolean): String =
            """
            {
              "data": $harHattUtbetaling,
              "status": "SUKSESS",
              "melding": "Innhenting av data var vellykket",
              "frontendFeilmelding": null,
              "stacktrace": null
            }
            """.trimIndent()

        private fun responseUtenData(): String =
            """
            {
              "data": null,
              "status": "FEILET",
              "melding": "Noe gikk galt",
              "frontendFeilmelding": null,
              "stacktrace": null
            }
            """.trimIndent()
    }
}
