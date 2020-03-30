package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.kontrakter.felles.oppgave.OpprettOppgave
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

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun opprettJournalføringsoppgave(journalpost: Journalpost): OppgaveResponse {
        logger.info("Oppretter journalføringsoppgave for papirsøknad")
        val uri = URI.create("$integrasjonUri/oppgave")
        val request = oppgaveMapper.mapTilOpprettOppgave(Oppgavetype.Journalføring, journalpost)

        return responseFra(uri, request)
    }

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun opprettBehandleSakOppgave(journalpost: Journalpost): OppgaveResponse {
        logger.info("Oppretter \"Behandle sak\"-oppgave for digital søknad")
        val uri = URI.create("$integrasjonUri/oppgave")
        val request = oppgaveMapper.mapTilOpprettOppgave(Oppgavetype.BehandleSak, journalpost)

        return responseFra(uri, request)
    }

    private fun responseFra(uri: URI, request: OpprettOppgave): OppgaveResponse {
        return Result.runCatching {
            postForEntity<Ressurs<OppgaveResponse>>(uri, request)
        }.fold(
                onSuccess = { response -> assertGyldig(response) },
                onFailure = {
                    throw IntegrasjonException("Post-kall mot $uri feilet ved opprettelse av oppgave",
                                               it,
                                               uri,
                                               null)
                }
        )
    }

    private fun assertGyldig(ressurs: Ressurs<OppgaveResponse>?): OppgaveResponse {
        return when {
            ressurs == null -> error("Finner ikke ressurs")
            ressurs.data == null -> error("Ressurs mangler data")
            ressurs.status != Ressurs.Status.SUKSESS -> error("Ressurs returnerer 2xx men har ressurs status failure")

            else -> ressurs.data!!
        }
    }
}
