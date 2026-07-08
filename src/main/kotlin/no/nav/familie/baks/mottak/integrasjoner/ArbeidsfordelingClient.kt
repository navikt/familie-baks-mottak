package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.PersonIdent
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.arbeidsfordeling.Enhet
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.util.Optional.ofNullable

@Component
class ArbeidsfordelingClient(
    @param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonUri: URI,
    @Qualifier("integrasjonerRestClient") private val restClient: RestClient,
) {
    fun hentBehandlendeEnheterPåIdent(
        personIdent: String,
        tema: Tema,
        behandlingstype: Behandlingstype?,
    ): List<Enhet> {
        val uri =
            UriComponentsBuilder
                .fromUri(integrasjonUri)
                .pathSegment("arbeidsfordeling", "enhet", tema.toString())
                .queryParamIfPresent("behandlingstype", ofNullable(behandlingstype))
                .build()
                .encode()
                .toUri()

        return runCatching {
            restClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(PersonIdent(personIdent))
                .retrieve()
                .body<Ressurs<List<Enhet>>>()!!
        }.fold(
            onSuccess = { it.data ?: throw IntegrasjonException(it.melding, uri = uri, ident = personIdent) },
            onFailure = { throw IntegrasjonException("Feil ved henting av behandlende enheter på ident m/ tema $tema og behandlingstype $behandlingstype", it, uri, personIdent) },
        )
    }

    fun hentBehandlendeEnhetPåIdent(
        personIdent: String,
        tema: Tema,
        behandlingstype: Behandlingstype?,
    ): Enhet = hentBehandlendeEnheterPåIdent(personIdent, tema, behandlingstype).singleOrNull() ?: throw IllegalStateException("Forventet bare 1 enhet på ident men fantes flere")
}
