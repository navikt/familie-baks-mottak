package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.util.fristFerdigstillelse
import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.oppgave.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.NestedExceptionUtils
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.net.URI

private val logger = LoggerFactory.getLogger(OppgaveClient::class.java)

@Component
class OppgaveClient @Autowired constructor(
    @param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonUri: URI,
    @Qualifier("clientCredentials") restOperations: RestOperations,
    private val oppgaveMapper: OppgaveMapper
) : AbstractRestClient(restOperations, "integrasjon") {

    val secureLog: Logger = LoggerFactory.getLogger("secureLogger")

    fun opprettJournalføringsoppgave(journalpost: Journalpost, beskrivelse: String? = null): OppgaveResponse {
        logger.info("Oppretter journalføringsoppgave for ${if (journalpost.kanal == "NAV_NO") "digital søknad" else "papirsøknad"}")
        val uri = URI.create("$integrasjonUri/oppgave/opprett")
        val request = oppgaveMapper.mapTilOpprettOppgave(Oppgavetype.Journalføring, journalpost, beskrivelse)

        return responseFraOpprettOppgave(uri, request)
    }

    fun opprettBehandleSakOppgave(journalpost: Journalpost, beskrivelse: String? = null): OppgaveResponse {
        logger.info("Oppretter \"Behandle sak\"-oppgave for digital søknad")
        val uri = URI.create("$integrasjonUri/oppgave/opprett")
        val request = oppgaveMapper.mapTilOpprettOppgave(Oppgavetype.BehandleSak, journalpost, beskrivelse)

        return responseFraOpprettOppgave(uri, request)
    }


    @Retryable(
        value = [RuntimeException::class],
        maxAttempts = 3,
        backoff = Backoff(delayExpression = "\${retry.backoff.delay:5000}")
    )
    fun opprettVurderLivshendelseOppgave(dto: OppgaveVurderLivshendelseDto): OppgaveResponse {
        logger.info("Oppretter \"Vurder livshendelse\"-oppgave")

        val uri = URI.create("$integrasjonUri/oppgave/opprett")
        val request = OpprettOppgaveRequest(
            ident = OppgaveIdentV2(dto.aktørId, IdentGruppe.AKTOERID),
            saksId = dto.saksId,
            journalpostId = null,
            tema = Tema.BAR,
            oppgavetype = Oppgavetype.VurderLivshendelse,
            fristFerdigstillelse = fristFerdigstillelse(),
            beskrivelse = dto.beskrivelse,
            enhetsnummer = dto.enhetsId,
            behandlingstema = dto.behandlingstema,
            behandlingstype = null
        )

        secureLog.info("Oppretter vurderLivshendlseOppgave $request")

        return responseFraOpprettOppgave(uri, request)
    }

    @Retryable(
        value = [RuntimeException::class],
        maxAttempts = 3,
        backoff = Backoff(delayExpression = "\${retry.backoff.delay:5000}")
    )
    fun oppdaterOppgaveBeskrivelse(patchOppgave: Oppgave, beskrivelse: String): OppgaveResponse {
        val uri = URI.create("$integrasjonUri/oppgave/${patchOppgave.id}/oppdater")

        return Result.runCatching {
            patchForEntity<Ressurs<OppgaveResponse>>(uri, patchOppgave.copy(beskrivelse = beskrivelse))
        }.fold(
            onSuccess = { response -> assertGyldig(response) },
            onFailure = {
                throw IntegrasjonException(
                    "Patch-kall mot $uri feilet ved oppdatering av oppgave",
                    it,
                    uri,
                    null
                )
            }
        )
    }


    @Retryable(
        value = [RuntimeException::class],
        maxAttempts = 3,
        backoff = Backoff(delayExpression = "\${retry.backoff.delay:5000}")
    )
    fun finnOppgaver(journalpostId: String, oppgavetype: Oppgavetype?): List<Oppgave> {
        logger.info("Søker etter aktive oppgaver for $journalpostId")
        val uri = URI.create("$integrasjonUri/oppgave/v4")
        val request = FinnOppgaveRequest(
            journalpostId = journalpostId,
            tema = Tema.BAR,
            oppgavetype = oppgavetype
        )

        return Result.runCatching {
            postForEntity<Ressurs<FinnOppgaveResponseDto>>(uri, request)
        }.fold(
            onSuccess = { response -> assertGyldig(response).oppgaver },
            onFailure = {
                secureLogger.error(
                    "Finn oppgaver feilet mot $uri og request: $request",
                    NestedExceptionUtils.getMostSpecificCause(it)
                )
                throw IntegrasjonException(
                    "GET $uri feilet ved henting av oppgaver",
                    it,
                    uri,
                    null
                )
            }
        )
    }

    @Retryable(
        value = [RuntimeException::class],
        maxAttempts = 3,
        backoff = Backoff(delayExpression = "\${retry.backoff.delay:5000}")
    )
    fun finnOppgaverPåAktørId(aktørId: String, oppgavetype: Oppgavetype): List<Oppgave> {
        logger.info("Søker etter aktive oppgaver for aktørId $aktørId")
        val uri = URI.create("$integrasjonUri/oppgave/v4")
        val request = FinnOppgaveRequest(
            aktørId = aktørId,
            tema = Tema.BAR,
            oppgavetype = oppgavetype
        )

        return Result.runCatching {
            postForEntity<Ressurs<FinnOppgaveResponseDto>>(uri, request)
        }.fold(
            onSuccess = { response -> assertGyldig(response).oppgaver },
            onFailure = {
                secureLogger.error(
                    "Finn oppgave feilet for $aktørId og $oppgavetype",
                    NestedExceptionUtils.getMostSpecificCause(it)
                )
                throw IntegrasjonException(
                    "GET $uri feilet ved henting av oppgaver",
                    it,
                    uri,
                    null
                )
            }
        )
    }


    private fun responseFraOpprettOppgave(uri: URI, request: OpprettOppgaveRequest): OppgaveResponse {
        return Result.runCatching {
            logger.info("Sender OpprettOppgaveRequest med beskrivelse: ${request.beskrivelse}")
            postForEntity<Ressurs<OppgaveResponse>>(uri, request)
        }.fold(
            onSuccess = { response -> assertGyldig(response) },
            onFailure = {
                secureLogger.error(
                    "Opprett oppgave feilet mot $uri og request: $request",
                    NestedExceptionUtils.getMostSpecificCause(it)
                )
                log.warn("Post-kall mot $uri feilet ved opprettelse av oppgave", it)
                throw IntegrasjonException(
                    "Post-kall mot $uri feilet ved opprettelse av oppgave",
                    it,
                    uri,
                    null
                )
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

data class OppgaveVurderLivshendelseDto(
    val aktørId: String,
    val beskrivelse: String,
    val saksId: String,
    val behandlingstema: String,
    val enhetsId: String,
    val behandlesAvApplikasjon: String? = null,
)