@file:Suppress("LongLine")

package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import io.mockk.every
import io.mockk.mockkStatic
import no.nav.familie.baks.mottak.DevLauncher
import no.nav.familie.http.client.RessursException
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.Ressurs.Companion.success
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.FinnOppgaveResponseDto
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.log.NavHttpHeaders
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(
    classes = [DevLauncher::class],
    properties = ["FAMILIE_INTEGRASJONER_API_URL=http://localhost:28085/api", "NORG2_API_URL=http://localhost:28085/norg2/"],
)
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OppgaveClientTest {

    @Autowired
    @Qualifier("oppgaveClient")
    lateinit var oppgaveClient: OppgaveClient

    @BeforeEach
    fun setUp() {
        stubFor(
            get(urlEqualTo("/norg2/api/v1/enhet/9999"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(Enhet("9999", "enhetNavn", true, "Aktiv"))),
                ),
        )
    }

    @AfterEach
    fun cleanUp() {
        MDC.clear()
        resetAllRequests()
    }

    @Test
    @Tag("integration")
    fun `Opprett journalføringsoppgave skal returnere oppgave id`() {
        MDC.put("callId", "opprettJournalføringsoppgave")
        stubFor(
            post(urlEqualTo("/api/oppgave/opprett"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            objectMapper.writeValueAsString(success(OppgaveResponse(oppgaveId = 1234))),
                        ),
                ),
        )
        mockkStatic(LocalDateTime::class)
        every { LocalDateTime.now() } returns LocalDateTime.of(2020, 4, 1, 0, 0)

        val opprettOppgaveResponse =
            oppgaveClient.opprettJournalføringsoppgave(journalPost)

        assertThat(opprettOppgaveResponse.oppgaveId).isEqualTo(1234)
        verify(
            anyRequestedFor(anyUrl())
                .withHeader(NavHttpHeaders.NAV_CALL_ID.asString(), equalTo("opprettJournalføringsoppgave"))
                .withHeader(NavHttpHeaders.NAV_CONSUMER_ID.asString(), equalTo("familie-baks-mottak"))
                .withRequestBody(
                    equalToJson(
                        forventetOpprettOppgaveRequestJson(
                            journalpostId = "1234567",
                            oppgavetype = "Journalføring",
                            behandlingstema = Behandlingstema.OrdinærBarnetrygd.value,
                            beskrivelse = "Tittel",
                        ),
                        true,
                        true,
                    ),
                ),
        )
    }

    @Test
    @Tag("integration")
    fun `Opprett behandleSak-oppgave skal returnere oppgave id`() {
        MDC.put("callId", "opprettJournalføringsoppgave")
        stubFor(
            post(urlEqualTo("/api/oppgave/opprett"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            objectMapper.writeValueAsString(success(OppgaveResponse(oppgaveId = 1234))),
                        ),
                ),
        )
        mockkStatic(LocalDateTime::class)
        every { LocalDateTime.now() } returns LocalDateTime.of(2020, 4, 1, 0, 0)

        val opprettOppgaveResponse =
            oppgaveClient.opprettBehandleSakOppgave(journalPost)

        assertThat(opprettOppgaveResponse.oppgaveId).isEqualTo(1234)
        verify(
            anyRequestedFor(anyUrl())
                .withHeader(NavHttpHeaders.NAV_CALL_ID.asString(), equalTo("opprettJournalføringsoppgave"))
                .withHeader(NavHttpHeaders.NAV_CONSUMER_ID.asString(), equalTo("familie-baks-mottak"))
                .withRequestBody(
                    equalToJson(
                        forventetOpprettOppgaveRequestJson(
                            journalpostId = "1234567",
                            oppgavetype = "BehandleSak",
                            behandlingstema = Behandlingstema.OrdinærBarnetrygd.value,
                            beskrivelse = "Tittel",
                        ),
                        true,
                        true,
                    ),
                ),
        )
    }

    @Test
    @Tag("integration")
    fun `Opprett oppgave skal kaste feil hvis response er ugyldig`() {
        stubFor(
            post(urlEqualTo("/api/oppgave/opprett"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withBody(objectMapper.writeValueAsString(Ressurs.failure<String>("test"))),
                ),
        )

        assertThatThrownBy {
            oppgaveClient.opprettJournalføringsoppgave(journalPost)
        }.isInstanceOf(IntegrasjonException::class.java)
            .hasCauseInstanceOf(RessursException::class.java)
            .hasMessageContaining("feilet ved opprettelse av oppgave")

        assertThatThrownBy {
            oppgaveClient.opprettBehandleSakOppgave(journalPost)
        }.isInstanceOf(IntegrasjonException::class.java)
            .hasCauseInstanceOf(RessursException::class.java)
            .hasMessageContaining("feilet ved opprettelse av oppgave")
    }

    @Test
    @Tag("integration")
    fun `Opprett oppgave skal kaste feil hvis journalposten ikke inneholder noen dokumenter`() {
        val journalpostUtenDokumenter = journalPost.copy(dokumenter = listOf())
        assertThatThrownBy {
            oppgaveClient.opprettJournalføringsoppgave(journalpostUtenDokumenter)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Journalpost ${journalpostUtenDokumenter.journalpostId} mangler dokumenter")
    }

    @Test
    @Tag("integration")
    fun `Finn oppgaver skal returnere liste med 1 oppgave`() {
        stubFor(
            post(urlEqualTo("/api/oppgave/v4"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            objectMapper.writeValueAsString(
                                success(
                                    FinnOppgaveResponseDto(
                                        antallTreffTotalt = 1,
                                        oppgaver = listOf(Oppgave(id = 1234)),
                                    ),
                                ),
                            ),
                        ),
                ),
        )

        val oppgaveListe = oppgaveClient.finnOppgaver(journalPost.journalpostId, Oppgavetype.Journalføring)

        assertThat(oppgaveListe).hasSize(1)
        assertThat(oppgaveListe.first().id).isEqualTo(1234)
    }

    @Test
    @Tag("integration")
    fun `Finn oppgaver skal returnere tom liste`() {
        stubFor(
            post(urlEqualTo("/api/oppgave/v4"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            objectMapper.writeValueAsString(
                                success(
                                    FinnOppgaveResponseDto(
                                        antallTreffTotalt = 0,
                                        oppgaver = emptyList(),
                                    ),
                                ),
                            ),
                        ),
                ),
        )

        val oppgaveListe = oppgaveClient.finnOppgaver(journalPost.journalpostId, Oppgavetype.Journalføring)

        assertThat(oppgaveListe).isEmpty()
    }

    private fun forventetOpprettOppgaveRequestJson(
        journalpostId: String,
        oppgavetype: String,
        behandlingstema: String,
        beskrivelse: String,
    ): String {
        return "{\n" +
            "  \"ident\": {\n" +
            "    \"ident\": \"1234567891011\",\n" +
            "    \"gruppe\": \"AKTOERID\"\n" +
            "  },\n" +
            "  \"enhetsnummer\": \"9999\",\n" +
            "  \"journalpostId\": \"$journalpostId\",\n" +
            "  \"tema\": \"BAR\",\n" +
            "  \"oppgavetype\": \"$oppgavetype\",\n" +
            "  \"behandlingstema\": \"$behandlingstema\",\n" +
            "  \"fristFerdigstillelse\": \"2020-04-01\",\n" +
            "  \"aktivFra\": \"${LocalDate.now()}\",\n" +
            "  \"beskrivelse\": \"${beskrivelse}\",\n" +
            "  \"prioritet\": \"NORM\"\n" +
            "}"
    }

    companion object {

        private val journalPost = Journalpost(
            "1234567",
            Journalposttype.I,
            Journalstatus.MOTTATT,
            Tema.BAR.name,
            "behandlingstemaFraJournalpost",
            null,
            Bruker("1234567891011", BrukerIdType.AKTOERID),
            "9999",
            "kanal",
            listOf(
                DokumentInfo(
                    "Tittel",
                    "NAV- 99.00.07",
                    null,
                    null,
                ),
            ),
        )
    }
}
