package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.domene.personopplysning.Person
import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.log.NavHttpHeaders
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestOperations
import java.net.URI

private val logger = LoggerFactory.getLogger(PersonClient::class.java)

@Component
class PersonClient @Autowired constructor(@param:Value("\${FAMILIE_INTEGRASJONER_API_URL}")
                                           private val integrasjonerServiceUri: URI,
                                          @Qualifier("clientCredentials") restOperations: RestOperations)
    : AbstractRestClient(restOperations, "integrasjon.pdl") {

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun hentPersonMedRelasjoner(personIdent: String): Person {
        val uri = URI.create("$integrasjonerServiceUri/personopplysning/v1/info/BAR")
        logger.info("Henter personinfo fra $uri")
        return try {
            val headers = HttpHeaders().apply {
                add(NavHttpHeaders.NAV_PERSONIDENT.asString(), personIdent)
            }
            val response = getForEntity<Ressurs<Person>>(uri, headers)

            secureLogger.info("Personinfo for {}: {}", personIdent, response)
            response?.data ?: throw RuntimeException("Response eller data er null.")
        } catch (e: HttpStatusCodeException) {
            logger.info("Feil mot TPS. status=${e.statusCode}, stacktrace=${e.stackTrace.toList()}")
            secureLogger.info("Feil mot TPS. msg=${e.message}, body=${e.responseBodyAsString}")
            throw RuntimeException("Kall mot integrasjon feilet ved uthenting av personinfo. ${e.statusCode} ${e.responseBodyAsString}")
        }
    }
}
