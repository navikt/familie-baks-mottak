package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.oppgave.*
import no.nav.familie.kontrakter.felles.oppgave.Tema
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = LoggerFactory.getLogger(OppgaveClient::class.java)

@Component
class OppgaveClient @Autowired constructor(@param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonUri: URI,
                                           @Qualifier("clientCredentials") restOperations: RestOperations)
    : AbstractRestClient(restOperations, "integrasjon") {

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun opprettJournalføringsoppgave(journalpostId: String): OppgaveResponse {
        logger.info("Oppretter journalføringsoppgave for papirsøknad")

        val uri = URI.create("$integrasjonUri/oppgave")
        val request = OpprettOppgave(ident = OppgaveIdent(ident = "", type = IdentType.Aktør),
                                     saksId = null,
                                     journalpostId = journalpostId,
                                     tema = Tema.BAR,
                                     oppgavetype = Oppgavetype.Journalføring,
                                     fristFerdigstillelse = LocalDate.now().plusDays(1),
                                     beskrivelse = lagOppgaveTekst(journalpostId),
                                     enhetsnummer = null,
                                     behandlingstema = "ab0180") // ordinær barnetrygd

        return Result.runCatching {
            postForEntity<Ressurs<OppgaveResponse>>(uri, request)
        }.fold(
            onSuccess = {
                when {
                    it == null -> error("Finner ikke ressurs")
                    it.data == null -> error("Ressurs mangler data")
                    it.status != Ressurs.Status.SUKSESS -> error("Ressurs returnerer 2xx men har ressurs status failure")

                    else -> it.data!!
                }
            },
            onFailure = {
                throw IntegrasjonException("Kall mot integrasjon feilet ved opprettelse av oppgave", it, uri, null)
            }
        )
    }

    private fun lagOppgaveTekst(journalpostId: String): String {
        //TODO Tekst skal oppdateres når man får et forslag
        var oppgaveTekst =
            "----- Opprettet av familie-ba-sak ${LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)} --- \n"
        oppgaveTekst += "Ny, midlertidig journalføring av papirsøknad om barnetrygd med journalpostId $journalpostId \n"
        return oppgaveTekst
    }
}
