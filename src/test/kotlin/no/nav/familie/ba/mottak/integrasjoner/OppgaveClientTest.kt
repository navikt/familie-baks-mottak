@file:Suppress("LongLine")

package no.nav.familie.ba.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.*
import io.mockk.every
import io.mockk.mockkStatic
import no.nav.familie.ba.mottak.config.ApplicationConfig
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.Ressurs.Companion.success
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.log.NavHttpHeaders
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.time.LocalDateTime

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
        stubFor(post(urlEqualTo("/api/oppgave"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    objectMapper.writeValueAsString(success(OppgaveResponse(oppgaveId = 1234))))))
        mockkStatic(LocalDateTime::class)
        every { LocalDateTime.now() } returns LocalDateTime.of(2020,4,1,0,0)

        val opprettOppgaveResponse =
                oppgaveClient.opprettJournalføringsoppgave(journalPost)

        assertThat(opprettOppgaveResponse.oppgaveId).isEqualTo(1234)
        verify(anyRequestedFor(anyUrl())
                       .withHeader(NavHttpHeaders.NAV_CALL_ID.asString(), equalTo("opprettJournalføringsoppgave"))
                       .withHeader(NavHttpHeaders.NAV_CONSUMER_ID.asString(), equalTo("familie-ba-mottak"))
                       .withRequestBody(equalToJson(forventetOpprettOppgaveRequestJson(journalpostId = "1234567",
                                                                                       oppgavetype = "Journalføring",
                                                                                       behandlingstema = "behandlingstemaFraJournalpost"))))
    }

    @Test
    @Tag("integration")
    fun `Opprett behandleSak-oppgave skal returnere oppgave id`() {
        MDC.put("callId", "opprettJournalføringsoppgave")
        stubFor(post(urlEqualTo("/api/oppgave"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    objectMapper.writeValueAsString(success(OppgaveResponse(oppgaveId = 1234))))))
        mockkStatic(LocalDateTime::class)
        every { LocalDateTime.now() } returns LocalDateTime.of(2020,4,1,0,0)

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
        stubFor(post(urlEqualTo("/api/oppgave"))
                        .willReturn(aResponse()
                                            .withStatus(500)
                                            .withBody(objectMapper.writeValueAsString(Ressurs.failure<String>("test")))))

        assertThatThrownBy {
            oppgaveClient.opprettJournalføringsoppgave(journalPost)
        }.isInstanceOf(IntegrasjonException::class.java)
                .hasMessageContaining("Error mot http://localhost:28085/api/oppgave status=500 body={")

        assertThatThrownBy {
            oppgaveClient.opprettBehandleSakOppgave(journalPost)
        }.isInstanceOf(IntegrasjonException::class.java)
                .hasMessageContaining("Error mot http://localhost:28085/api/oppgave status=500 body={")
    }

    @Test
    @Tag("integration")
    fun `Finn oppgaver skal returnere liste med 1 oppgave`() {
        stubFor(get(urlEqualTo("/api/oppgave?tema=BAR&oppgavetype=JFR&journalpostId=${journalPost.journalpostId}"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    objectMapper.writeValueAsString(success(listOf(OppgaveDto(id = 1234)))))))

        val oppgaveListe = oppgaveClient.finnOppgaver(journalPost.journalpostId, Oppgavetype.Journalføring)

        assertThat(oppgaveListe).hasSize(1)
        assertThat(oppgaveListe.first().id).isEqualTo(1234)
    }

    @Test
    @Tag("integration")
    fun `Finn oppgaver skal returnere tom liste`() {
        stubFor(get(urlEqualTo("/api/oppgave?tema=BAR&oppgavetype=JFR&journalpostId=${journalPost.journalpostId}"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    objectMapper.writeValueAsString(success(emptyList<OppgaveDto>())))))

        val oppgaveListe = oppgaveClient.finnOppgaver(journalPost.journalpostId, Oppgavetype.Journalføring)

        assertThat(oppgaveListe).isEmpty()
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
               "  \"fristFerdigstillelse\": \"2020-04-01\",\n" +
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