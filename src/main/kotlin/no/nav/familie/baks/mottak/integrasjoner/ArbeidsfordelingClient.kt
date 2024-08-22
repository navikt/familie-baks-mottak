package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
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
    fun hentBehandlendeEnhetPåIdent(
        personIdent: String,
        tema: Tema,
    ): Enhet {
        val uri = URI.create("$integrasjonUri/arbeidsfordeling/enhet/$tema")
        return runCatching {
            postForEntity<Ressurs<Enhet>>(uri, RestPersonIdent(personIdent))
        }.fold(
            onSuccess = { it.data ?: throw IntegrasjonException(it.melding, uri = uri, ident = personIdent) },
            onFailure = { throw IntegrasjonException("Feil ved henting av behandlende enhet på ident m/ tema $tema", it, uri, personIdent) },
        )
    }
}
