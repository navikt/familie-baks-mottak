package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggle
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleService
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.arbeidsfordeling.Enhet
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.core.io.ClassPathResource
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import java.net.URI

@Tag("integration")
class IntegrasjonerClientProviderTest {
    private val featureToggleService = mockk<FeatureToggleService>()
    private val wiremockServer = WireMockServer(wireMockConfig().dynamicPort())
    private lateinit var clientProvider: IntegrasjonerClientProvider
    private lateinit var journalpostClient: JournalpostClient
    private val journalpost = Journalpost("123", Journalposttype.I, Journalstatus.MOTTATT)

    @BeforeEach
    fun setUp() {
        wiremockServer.start()
        every { featureToggleService.isEnabled(FeatureToggle.BRUK_FAMILIE_INTEGRASJONER_I_GCP, defaultValue = false) } returns false
        clientProvider =
            IntegrasjonerClientProvider(
                integrasjonFssUri = URI.create("${wiremockServer.baseUrl()}/fss"),
                restClientFss = RestClient.builder().defaultHeader("Authorization", "Bearer test-fss").build(),
                integrasjonGcpUri = URI.create("${wiremockServer.baseUrl()}/gcp"),
                restClientGcp = RestClient.builder().defaultHeader("Authorization", "Bearer test-gcp").build(),
                featureToggleService = featureToggleService,
            )
        journalpostClient = JournalpostClient(clientProvider)
    }

    @AfterEach
    fun tearDown() {
        wiremockServer.stop()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `alle integrasjonsklientene skal velge URL og autentisert klient fra samme cluster`(brukGcp: Boolean) {
        every { featureToggleService.isEnabled(FeatureToggle.BRUK_FAMILIE_INTEGRASJONER_I_GCP, defaultValue = false) } returns brukGcp
        val cluster = if (brukGcp) "gcp" else "fss"
        val journalpostSti = "/$cluster/journalpost?journalpostId=123"
        val arbeidsfordelingSti = "/$cluster/arbeidsfordeling/enhet/KON"
        val ferdigstillSti = "/$cluster/arkiv/v2/123/ferdigstill?journalfoerendeEnhet=9999"
        val oppgaveSti = "/$cluster/oppgave/opprett"
        val barnetrygdSti = "/$cluster/baks/versjonertsoknad/ba/123"
        val kontantstøtteSti = "/$cluster/baks/versjonertsoknad/ks/123"

        wiremockServer.stubFor(get(urlEqualTo(journalpostSti)).willReturn(okJson(journalpostResponse())))
        wiremockServer.stubFor(
            post(urlEqualTo(arbeidsfordelingSti)).willReturn(
                okJson(jsonMapper.writeValueAsString(Ressurs.success(listOf(Enhet("9999", "Testenhet"))))),
            ),
        )
        wiremockServer.stubFor(put(urlEqualTo(ferdigstillSti)).willReturn(okJson(jsonMapper.writeValueAsString(Ressurs.success("OK")))))
        wiremockServer.stubFor(
            post(urlEqualTo(oppgaveSti)).willReturn(okJson(jsonMapper.writeValueAsString(Ressurs.success(OppgaveResponse(123))))),
        )
        wiremockServer.stubFor(get(urlEqualTo(barnetrygdSti)).willReturn(okJson(hentResponsFraFil("versjonertBarnetrygdSøknadV9.json"))))
        wiremockServer.stubFor(get(urlEqualTo(kontantstøtteSti)).willReturn(okJson(hentResponsFraFil("versjonertKontantstøtteSøknadV6.json"))))

        assertThat(journalpostClient.hentJournalpost("123").journalpostId).isEqualTo("123")
        assertThat(ArbeidsfordelingClient(clientProvider).hentBehandlendeEnhetPåIdent("test-ident", Tema.KON, null).enhetId).isEqualTo("9999")
        DokarkivClient(clientProvider).ferdigstillJournalpost("123")
        assertThat(
            OppgaveClient(clientProvider, mockk())
                .opprettVurderLivshendelseOppgave(
                    OppgaveVurderLivshendelseDto("test-aktor", "Testoppgave", "test-sak", Tema.BAR, null),
                ).oppgaveId,
        ).isEqualTo(123)
        val søknadClient = BaksVersjonertSøknadClient(clientProvider)
        assertThat(søknadClient.hentVersjonertBarnetrygdSøknad("123").barnetrygdSøknad.kontraktVersjon).isEqualTo(9)
        assertThat(søknadClient.hentVersjonertKontantstøtteSøknad("123").kontantstøtteSøknad.kontraktVersjon).isEqualTo(6)

        listOf(journalpostSti, arbeidsfordelingSti, ferdigstillSti, oppgaveSti, barnetrygdSti, kontantstøtteSti).forEach { sti ->
            wiremockServer.verify(1, anyRequestedFor(urlEqualTo(sti)).withHeader("Authorization", equalTo("Bearer test-$cluster")))
        }
        wiremockServer.verify(6, anyRequestedFor(anyUrl()))
        verify(exactly = 6) { featureToggleService.isEnabled(FeatureToggle.BRUK_FAMILIE_INTEGRASJONER_I_GCP, defaultValue = false) }
    }

    @Test
    fun `skal kunne bytte til GCP og tilbake til FSS uten omstart`() {
        every { featureToggleService.isEnabled(FeatureToggle.BRUK_FAMILIE_INTEGRASJONER_I_GCP, defaultValue = false) } returnsMany listOf(false, true, false)
        listOf("fss", "gcp").forEach { cluster ->
            wiremockServer.stubFor(get(urlEqualTo("/$cluster/journalpost?journalpostId=123")).willReturn(okJson(journalpostResponse())))
        }

        repeat(3) { journalpostClient.hentJournalpost("123") }

        wiremockServer.verify(2, getRequestedFor(urlEqualTo("/fss/journalpost?journalpostId=123")).withHeader("Authorization", equalTo("Bearer test-fss")))
        wiremockServer.verify(1, getRequestedFor(urlEqualTo("/gcp/journalpost?journalpostId=123")).withHeader("Authorization", equalTo("Bearer test-gcp")))
        verify(exactly = 3) { featureToggleService.isEnabled(FeatureToggle.BRUK_FAMILIE_INTEGRASJONER_I_GCP, defaultValue = false) }
    }

    @Test
    fun `skal ikke falle tilbake til FSS når et kall til GCP feiler`() {
        every { featureToggleService.isEnabled(FeatureToggle.BRUK_FAMILIE_INTEGRASJONER_I_GCP, defaultValue = false) } returns true
        wiremockServer.stubFor(get(urlEqualTo("/gcp/journalpost?journalpostId=123")).willReturn(serverError()))
        wiremockServer.stubFor(get(urlEqualTo("/fss/journalpost?journalpostId=123")).willReturn(okJson(journalpostResponse())))

        val feil = assertThrows<IllegalStateException> { journalpostClient.hentJournalpost("123") }

        assertThat(feil.cause).isInstanceOf(HttpServerErrorException::class.java)
        wiremockServer.verify(1, getRequestedFor(urlEqualTo("/gcp/journalpost?journalpostId=123")).withHeader("Authorization", equalTo("Bearer test-gcp")))
        wiremockServer.verify(0, getRequestedFor(urlEqualTo("/fss/journalpost?journalpostId=123")))
        verify(exactly = 1) { featureToggleService.isEnabled(FeatureToggle.BRUK_FAMILIE_INTEGRASJONER_I_GCP, defaultValue = false) }
    }

    private fun journalpostResponse(): String = jsonMapper.writeValueAsString(Ressurs.success(journalpost))

    private fun hentResponsFraFil(filnavn: String): String = ClassPathResource("testdata/$filnavn").inputStream.bufferedReader().use { it.readText() }
}
