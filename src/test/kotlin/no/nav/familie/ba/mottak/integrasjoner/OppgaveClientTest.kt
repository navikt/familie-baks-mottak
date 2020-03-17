package no.nav.familie.ba.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.*
import no.nav.familie.ba.mottak.config.ApplicationConfig
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.Ressurs.Companion.success
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.log.NavHttpHeaders
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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

@SpringBootTest(classes = [ApplicationConfig::class], properties = ["FAMILIE_INTEGRASJONER_API_URL=http://localhost:28085/api"])
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OppgaveClientTest {

    @Autowired
    @Qualifier("oppgaveClient")
    lateinit var oppgaveClient: OppgaveClient

    @AfterEach
    fun cleanUp() {
        MDC.clear()
        resetAllRequests()
    }

    @Test
    @Tag("integration")
    fun `Opprett journalføringsoppgave skal returnere oppgave id`() {
        MDC.put("callId", "opprettJournalføringsoppgave")
        stubFor(post(urlEqualTo("/api/oppgave/"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    objectMapper.writeValueAsString(success(OppgaveResponse(oppgaveId = 1234))))))

        val opprettOppgaveResponse =
                oppgaveClient.opprettJournalføringsoppgave(journalPost)

        assertThat(opprettOppgaveResponse.oppgaveId).isEqualTo(1234)
        verify(anyRequestedFor(anyUrl())
                       .withHeader(NavHttpHeaders.NAV_CALL_ID.asString(), equalTo("opprettJournalføringsoppgave"))
                       .withHeader(NavHttpHeaders.NAV_CONSUMER_ID.asString(), equalTo("familie-ba-mottak"))
                       .withRequestBody(equalToJson(forventetOpprettOppgaveRequestJson(journalpostId = "1234567",
                                                                                       oppgavetype = "Journalføring",
                                                                                       behandlingstema = "ab0180"))))
    }

    @Test
    @Tag("integration")
    fun `Opprett behandleSak-oppgave skal returnere oppgave id`() {
        MDC.put("callId", "opprettJournalføringsoppgave")
        stubFor(post(urlEqualTo("/api/oppgave/"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    objectMapper.writeValueAsString(success(OppgaveResponse(oppgaveId = 1234))))))

        val opprettOppgaveResponse =
                oppgaveClient.opprettBehandleSakOppgave(journalPost)

        assertThat(opprettOppgaveResponse.oppgaveId).isEqualTo(1234)
        verify(anyRequestedFor(anyUrl())
                       .withHeader(NavHttpHeaders.NAV_CALL_ID.asString(), equalTo("opprettJournalføringsoppgave"))
                       .withHeader(NavHttpHeaders.NAV_CONSUMER_ID.asString(), equalTo("familie-ba-mottak"))
                       .withRequestBody(equalToJson(
                               forventetOpprettOppgaveRequestJson(journalpostId = "1234567",
                                                                  oppgavetype = "BehandleSak",
                                                                  behandlingstema = "behandlingstemaFraJournalpost"))))
    }

    @Test
    @Tag("integration")
    fun `Opprett oppgave skal kaste feil hvis response er ugyldig`() {
        stubFor(post(urlEqualTo("/api/oppgave/"))
                        .willReturn(aResponse()
                                            .withStatus(500)
                                            .withBody(objectMapper.writeValueAsString(Ressurs.failure<String>("test")))))

        Assertions.assertThatThrownBy {
                    oppgaveClient.opprettJournalføringsoppgave(journalPost)
                }.isInstanceOf(IntegrasjonException::class.java)
                .hasMessageContaining("Error mot http://localhost:28085/api/oppgave/ status=500 body={")

        Assertions.assertThatThrownBy {
                    oppgaveClient.opprettBehandleSakOppgave(journalPost)
                }.isInstanceOf(IntegrasjonException::class.java)
                .hasMessageContaining("Error mot http://localhost:28085/api/oppgave/ status=500 body={")
    }

    private fun forventetOpprettOppgaveRequestJson(journalpostId: String,
                                                   oppgavetype: String,
                                                   behandlingstema: String): String {
        return "{\n" +
               "  \"ident\": {\n" +
               "    \"ident\": \"1234567891011\",\n" +
               "    \"type\": \"Aktør\"\n" +
               "  },\n" +
               "  \"enhetsnummer\": \"9999\",\n" +
               "  \"saksId\": null,\n" +
               "  \"journalpostId\": \"$journalpostId\",\n" +
               "  \"tema\": \"BAR\",\n" +
               "  \"oppgavetype\": \"$oppgavetype\",\n" +
               "  \"behandlingstema\": \"$behandlingstema\",\n" +
               "  \"fristFerdigstillelse\": \"${LocalDate.now().plusDays(2)}\",\n" +
               "  \"aktivFra\": \"${LocalDate.now()}\",\n" +
               "  \"beskrivelse\": \"\"\n" +
               "}"
    }

    companion object {
        private val journalPost = Journalpost("1234567",
                                              Journalposttype.I,
                                              Journalstatus.MOTTATT,
                                              "tema",
                                              "behandlingstemaFraJournalpost",
                                              null,
                                              Bruker("1234567891011", BrukerIdType.AKTOERID),
                                              "9999",
                                              "kanal",
                                              listOf())
    }
}