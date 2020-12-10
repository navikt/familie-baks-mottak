package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.oppgave.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.net.URI

private val logger = LoggerFactory.getLogger(OppgaveClient::class.java)

@Component
class OppgaveClient @Autowired constructor(@param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonUri: URI,
                                           @Qualifier("clientCredentials") restOperations: RestOperations,
                                           private val oppgaveMapper: OppgaveMapper)
    : AbstractRestClient(restOperations, "integrasjon") {

    fun opprettJournalføringsoppgave(journalpost: Journalpost, beskrivelse: String? = null): OppgaveResponse {
        logger.info("Oppretter journalføringsoppgave for papirsøknad")
        val uri = URI.create("$integrasjonUri/oppgave/opprett")
        val request = oppgaveMapper.mapTilOpprettOppgave(Oppgavetype.Journalføring, journalpost, beskrivelse)

        return responseFra(uri, request)
    }

    fun opprettBehandleSakOppgave(journalpost: Journalpost, beskrivelse: String? = null): OppgaveResponse {
        logger.info("Oppretter \"Behandle sak\"-oppgave for digital søknad")
        val uri = URI.create("$integrasjonUri/oppgave/opprett")
        val request = oppgaveMapper.mapTilOpprettOppgave(Oppgavetype.BehandleSak, journalpost, beskrivelse)

        return responseFra(uri, request)
    }

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun finnOppgaver(journalpostId: String, oppgavetype: Oppgavetype?): List<Oppgave> {
        logger.info("Søker etter aktive oppgaver for $journalpostId")
        val uri = URI.create("$integrasjonUri/oppgave/v4")
        val request = FinnOppgaveRequest(journalpostId = journalpostId,
                                         tema = Tema.BAR,
                                         oppgavetype = oppgavetype)

        return Result.runCatching {
            postForEntity<Ressurs<FinnOppgaveResponseDto>>(uri, request)
        }.fold(
                onSuccess = { response -> assertGyldig(response).oppgaver },
                onFailure = {
                    throw IntegrasjonException("GET $uri feilet ved henting av oppgaver",
                                               it,
                                               uri,
                                               null)
                }
        )
    }


    private fun responseFra(uri: URI, request: OpprettOppgaveRequest): OppgaveResponse {
        return Result.runCatching {
            logger.info("Sender OpprettOppgaveRequest med beskrivelse: ${request.beskrivelse}")
            postForEntity<Ressurs<OppgaveResponse>>(uri, request)
        }.fold(
                onSuccess = { response -> assertGyldig(response) },
                onFailure = {
                    log.warn("Post-kall mot $uri feilet ved opprettelse av oppgave", it)
                    throw IntegrasjonException("Post-kall mot $uri feilet ved opprettelse av oppgave",
                                               it,
                                               uri,
                                               null)
                }
        )
    }

    private fun <T> assertGyldig(ressurs: Ressurs<T>?): T {
        return when {
            ressurs == null -> error("Finner ikke ressurs")
            ressurs.data == null -> error("Ressurs mangler data")
            ressurs.status != Ressurs.Status.SUKSESS -> error("Ressurs returnerer 2xx men har ressurs status failure")

            else -> ressurs.data!!
        }
    }
}

data class OppgaveDto(val id: Long? = null)