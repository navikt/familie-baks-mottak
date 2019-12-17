package no.nav.familie.ba.mottak.integrasjoner

import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.ResponseEntity
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.URI

private val logger = LoggerFactory.getLogger(PersonService::class.java)
private val secureLogger = LoggerFactory.getLogger("secureLogger")
private const val OAUTH2_CLIENT_CONFIG_KEY = "ba-sak-clientcredentials"

@Component
class SakService @Autowired constructor(@param:Value("\${FAMILIE_BA_SAK_API_URL}") private val sakServiceUri: String,
                                           restTemplateBuilderMedProxy: RestTemplateBuilder?,
                                           clientConfigurationProperties: ClientConfigurationProperties?,
                                           oAuth2AccessTokenService: OAuth2AccessTokenService?) : BaseService(OAUTH2_CLIENT_CONFIG_KEY, restTemplateBuilderMedProxy!!, clientConfigurationProperties!!, oAuth2AccessTokenService!!) {

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun sendTilSak(søknadsdataJson: String) {
        val uri = URI.create("$sakServiceUri/behandling/opprett")
        logger.info("Sender søknad til {}", uri)
        try {
            val response: ResponseEntity<String>? = postRequest(uri, søknadsdataJson)
            logger.info("Søknad sendt til sak. Status=${response?.statusCode}")
        } catch (e: RestClientResponseException) {
            logger.warn("Innsending til sak feilet. Responskode: {}, body: {}", e.rawStatusCode, e.responseBodyAsString)
            throw IllegalStateException("Innsending til sak feilet. Status: " + e.rawStatusCode + ", body: " + e.responseBodyAsString, e)
        } catch (e: RestClientException) {
            throw IllegalStateException("Innsending til sak feilet.", e)
        }
    }
}