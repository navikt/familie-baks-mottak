package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestOperations
import java.net.URI

private val logger = LoggerFactory.getLogger(HentEnhetClient::class.java)

@Component
class HentEnhetClient(
    @param:Value("\${NORG2_API_URL}") private val norg2Uri: URI,
    @Qualifier("restTemplateUnsecured") restOperations: RestOperations
) :

    AbstractRestClient(restOperations, "norg2") {

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delayExpression = "\${retry.backoff.delay:5000}"))
    @Cacheable("enhet", cacheManager = "dailyCacheManager")
    fun hentEnhet(enhetId: String): Enhet {
        val uri = URI.create("$norg2Uri/api/v1/enhet/$enhetId")
        logger.info("henter enhet med id $enhetId")
        return try {
            getForEntity(uri)
        } catch (e: RestClientException) {
            val responseBodyAsString = if (e is RestClientResponseException) e.responseBodyAsString else ""
            val statusCode = if (e is RestClientResponseException) e.rawStatusCode.toString() else ""
            logger.error("Henting av enhet med id $enhetId feilet. $statusCode $responseBodyAsString")
            error("Henting av enhet med id $enhetId feilet. $statusCode $responseBodyAsString")
        }
    }
}

data class Enhet(
    val enhetId: String,
    val navn: String,
    val oppgavebehandler: Boolean,
    val status: String
)
