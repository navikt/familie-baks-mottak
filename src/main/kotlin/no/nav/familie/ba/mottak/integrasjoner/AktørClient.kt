package no.nav.familie.ba.mottak.integrasjoner


import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.log.NavHttpHeaders
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestOperations
import java.net.URI

@Component
class AktørClient(@Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonUri: URI,
                  @Qualifier("clientCredentials") restOperations: RestOperations)
    : AbstractRestClient(restOperations, "aktør") {

    @Retryable(value = [IntegrasjonException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun hentAktørId(personident: String): String {
        if (personident.isEmpty()) {
            throw IntegrasjonException("Ved henting av aktør id er personident null eller tom")
        }
        val uri = URI.create("$integrasjonUri/aktoer/v1")
        return try {
            val response = getForEntity<Ressurs<MutableMap<*, *>>>(uri, HttpHeaders().medPersonident(personident))
            secureLogger.info("Vekslet inn fnr: {} til aktørId: {}", personident, response)
            val aktørId = response.data?.get("aktørId").toString()
            if (aktørId.isEmpty()) {
                throw IntegrasjonException(msg = "Kan ikke finne aktørId for ident", ident = personident)
            } else {
                aktørId
            }
        } catch (e: RestClientException) {
            throw IntegrasjonException("Ukjent feil ved henting av aktørId", e, uri, personident)
        }
    }

    fun hentPersonident(aktørId: String): String {
        if (aktørId.isEmpty()) {
            throw IntegrasjonException("Ved henting av personident er aktør id null eller tom")
        }
        val uri = URI.create("$integrasjonUri/aktoer/v1/fraaktorid")
        return try {
            val response = getForEntity<Ressurs<MutableMap<*, *>>>(uri, HttpHeaders().medAktørId(aktørId))
            secureLogger.info("Vekslet inn aktørId: {} til fnr: {}", aktørId, response)
            val personident = response.data?.get("personIdent").toString()
            if (personident.isEmpty()) {
                throw IntegrasjonException(msg = "Kan ikke finne personident for aktørId", ident = aktørId)
            } else {
                personident
            }
        } catch (e: RestClientException) {
            throw IntegrasjonException("Ukjent feil ved henting av personident", e, uri, aktørId)
        }
    }

    private fun HttpHeaders.medPersonident(personident: String): HttpHeaders {
        this.add(NavHttpHeaders.NAV_PERSONIDENT.asString(), personident)
        return this
    }

    private fun HttpHeaders.medAktørId(aktørId: String): HttpHeaders {
        this.add("Nav-Aktorid", aktørId)
        return this
    }
}
