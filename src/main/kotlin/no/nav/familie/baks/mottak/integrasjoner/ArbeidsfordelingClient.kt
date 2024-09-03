package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.PersonIdent
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.arbeidsfordeling.Enhet
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.net.URI

@Component
class ArbeidsfordelingClient(
    @param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonUri: URI,
    @Qualifier("clientCredentials") restOperations: RestOperations,
) : AbstractRestClient(restOperations, "integrasjon") {
    fun hentBehandlendeEnheterPåIdent(
        personIdent: String,
        tema: Tema,
    ): List<Enhet> {
        val uri = URI.create("$integrasjonUri/arbeidsfordeling/enhet/$tema")
        return runCatching {
            postForEntity<Ressurs<List<Enhet>>>(uri, PersonIdent(personIdent))
        }.fold(
            onSuccess = { it.data ?: throw IntegrasjonException(it.melding, uri = uri, ident = personIdent) },
            onFailure = { throw IntegrasjonException("Feil ved henting av behandlende enheter på ident m/ tema $tema", it, uri, personIdent) },
        )
    }

    fun hentBehandlendeEnhetPåIdent(
        personIdent: String,
        tema: Tema,
    ): Enhet =
        hentBehandlendeEnheterPåIdent(personIdent, tema).singleOrNull() ?: throw IllegalStateException("Forventet bare 1 enhet på ident men fantes flere")
}
