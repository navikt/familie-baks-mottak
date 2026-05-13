package no.nav.familie.baks.mottak.integrasjoner

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.URI

private val logger = LoggerFactory.getLogger(HentEnhetClient::class.java)

@Component
class HentEnhetClient(
    @param:Value("\${NORG2_API_URL}") private val norg2Uri: URI,
    @Qualifier("unauthenticatedRestClient") private val restClient: RestClient,
) {
    fun hentEnhet(enhetId: String): Enhet {
        val uri = URI.create("$norg2Uri/api/v1/enhet/$enhetId")
        logger.info("henter enhet med id $enhetId")
        return try {
            restClient
                .get()
                .uri(uri)
                .retrieve()
                .body(Enhet::class.java)!!
        } catch (e: RestClientException) {
            val responseBodyAsString = if (e is RestClientResponseException) e.responseBodyAsString else ""
            val statusCode = if (e is RestClientResponseException) e.statusCode.toString() else ""
            logger.error("Henting av enhet med id $enhetId feilet. $statusCode $responseBodyAsString")
            error("Henting av enhet med id $enhetId feilet. $statusCode $responseBodyAsString")
        }
    }
}

data class Enhet(
    val enhetId: String,
    val navn: String,
    val oppgavebehandler: Boolean,
    val status: String,
)
