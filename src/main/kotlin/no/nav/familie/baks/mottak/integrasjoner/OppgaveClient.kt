package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.util.fristFerdigstillelse
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.oppgave.FinnOppgaveRequest
import no.nav.familie.kontrakter.felles.oppgave.FinnOppgaveResponseDto
import no.nav.familie.kontrakter.felles.oppgave.IdentGruppe
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveIdentV2
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.kontrakter.felles.oppgave.OpprettOppgaveRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.NestedExceptionUtils
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

private val logger = LoggerFactory.getLogger(OppgaveClient::class.java)

@Component
class OppgaveClient
    @Autowired
    constructor(
        @param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonUri: URI,
        @Qualifier("integrasjonerRestClient") private val restClient: RestClient,
        private val oppgaveMapperService: OppgaveMapperService,
    ) {
        val secureLog: Logger = LoggerFactory.getLogger("secureLogger")

        fun opprettJournalføringsoppgave(
            journalpost: Journalpost,
            beskrivelse: String? = null,
        ): OppgaveResponse {
            logger.info("Oppretter journalføringsoppgave for ${if (journalpost.erDigitalKanal()) "digital søknad" else "papirsøknad"}")
            val uri = URI.create("$integrasjonUri/oppgave/opprett")
            val request =
                oppgaveMapperService.tilOpprettOppgaveRequest(
                    Oppgavetype.Journalføring,
                    journalpost,
                    beskrivelse,
                )
            secureLog.info("Oppretter journalføringsoppgave for ${journalpost.journalpostId} ${request.beskrivelse}")
            return responseFraOpprettOppgave(uri, request)
        }

        fun opprettVurderLivshendelseOppgave(dto: OppgaveVurderLivshendelseDto): OppgaveResponse {
            logger.info("Oppretter \"Vurder livshendelse\"-oppgave")

            val uri = URI.create("$integrasjonUri/oppgave/opprett")
            val request =
                OpprettOppgaveRequest(
                    ident = OppgaveIdentV2(dto.aktørId, IdentGruppe.AKTOERID),
                    saksId = dto.saksId,
                    journalpostId = null,
                    tema = dto.tema,
                    oppgavetype = Oppgavetype.VurderLivshendelse,
                    fristFerdigstillelse = fristFerdigstillelse(),
                    beskrivelse = dto.beskrivelse,
                    enhetsnummer = dto.enhetsId,
                    behandlingstema = dto.behandlingstema,
                    behandlingstype = dto.behandlingstype,
                    behandlesAvApplikasjon = dto.behandlesAvApplikasjon,
                )

            secureLog.info("Oppretter vurderLivshendlseOppgave $request")
            return responseFraOpprettOppgave(uri, request)
        }

        fun oppdaterOppgaveBeskrivelse(
            patchOppgave: Oppgave,
            beskrivelse: String,
        ): OppgaveResponse {
            val uri = URI.create("$integrasjonUri/oppgave/${patchOppgave.id}/oppdater")
            return Result
                .runCatching {
                    restClient
                        .patch()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(patchOppgave.copy(beskrivelse = beskrivelse))
                        .retrieve()
                        .body(object : ParameterizedTypeReference<Ressurs<OppgaveResponse>>() {})!!
                }.fold(
                    onSuccess = { response -> assertGyldig(response) },
                    onFailure = {
                        throw IntegrasjonException(
                            "Patch-kall mot $uri feilet ved oppdatering av oppgave",
                            it,
                            uri,
                            null,
                        )
                    },
                )
        }

        fun finnOppgaver(
            journalpostId: String,
            oppgavetype: Oppgavetype?,
        ): List<Oppgave> {
            logger.info("Søker etter aktive oppgaver for $journalpostId")
            val uri = URI.create("$integrasjonUri/oppgave/v4")
            val request = FinnOppgaveRequest(journalpostId = journalpostId, tema = Tema.BAR, oppgavetype = oppgavetype)
            return Result
                .runCatching {
                    restClient
                        .post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(object : ParameterizedTypeReference<Ressurs<FinnOppgaveResponseDto>>() {})!!
                }.fold(
                    onSuccess = { response -> assertGyldig(response).oppgaver },
                    onFailure = {
                        secureLog.error("Finn oppgaver feilet mot $uri og request: $request", NestedExceptionUtils.getMostSpecificCause(it))
                        throw IntegrasjonException("Post-kall $uri feilet ved henting av oppgaver", it, uri, null)
                    },
                )
        }

        fun finnOppgaverPåAktørId(
            aktørId: String,
            oppgavetype: Oppgavetype,
            tema: Tema,
        ): List<Oppgave> {
            secureLog.info("Søker etter aktive oppgaver med tema $tema for aktørId $aktørId")
            val uri = URI.create("$integrasjonUri/oppgave/v4")
            val request = FinnOppgaveRequest(aktørId = aktørId, tema = tema, oppgavetype = oppgavetype)
            return Result
                .runCatching {
                    restClient
                        .post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(object : ParameterizedTypeReference<Ressurs<FinnOppgaveResponseDto>>() {})!!
                }.fold(
                    onSuccess = { response -> assertGyldig(response).oppgaver },
                    onFailure = {
                        secureLog.error("Finn oppgave feilet for $aktørId og $oppgavetype", NestedExceptionUtils.getMostSpecificCause(it))
                        throw IntegrasjonException("Post-kall $uri feilet ved henting av oppgaver", it, uri, null)
                    },
                )
        }

        private fun responseFraOpprettOppgave(
            uri: URI,
            request: OpprettOppgaveRequest,
        ): OppgaveResponse =
            Result
                .runCatching {
                    secureLog.info("Sender OpprettOppgaveRequest $request")
                    restClient
                        .post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(object : ParameterizedTypeReference<Ressurs<OppgaveResponse>>() {})!!
                }.fold(
                    onSuccess = { response -> assertGyldig(response) },
                    onFailure = {
                        secureLog.error("Opprett oppgave feilet mot $uri og request: $request", NestedExceptionUtils.getMostSpecificCause(it))
                        logger.warn("Post-kall mot $uri feilet ved opprettelse av oppgave", it)
                        throw IntegrasjonException("Post-kall mot $uri feilet ved opprettelse av oppgave", it, uri, null)
                    },
                )

        private fun <T> assertGyldig(ressurs: Ressurs<T>?): T =
            when {
                ressurs == null -> error("Finner ikke ressurs")
                ressurs.data == null -> error("Ressurs mangler data")
                ressurs.status != Ressurs.Status.SUKSESS -> error("Ressurs returnerer 2xx men har ressurs status failure")
                else -> ressurs.data!!
            }
    }

data class OppgaveVurderLivshendelseDto(
    val aktørId: String,
    val beskrivelse: String,
    val saksId: String,
    val tema: Tema,
    val behandlingstema: String?,
    val enhetsId: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val behandlingstype: String? = null,
)
