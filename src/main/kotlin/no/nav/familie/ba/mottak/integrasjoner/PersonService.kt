package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.domene.personopplysning.Personinfo
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.net.URI

private val logger = LoggerFactory.getLogger(PersonService::class.java)
private val secureLogger = LoggerFactory.getLogger("secureLogger")
private const val OAUTH2_CLIENT_CONFIG_KEY = "integrasjoner-clientcredentials"

@Component
class PersonService @Autowired constructor(@param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonerServiceUri: URI,
                                                 restTemplateBuilderMedProxy: RestTemplateBuilder?,
                                                 clientConfigurationProperties: ClientConfigurationProperties?,
                                                 oAuth2AccessTokenService: OAuth2AccessTokenService?) : BaseService(OAUTH2_CLIENT_CONFIG_KEY, restTemplateBuilderMedProxy!!, clientConfigurationProperties!!, oAuth2AccessTokenService!!) {

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun hentPersonMedRelasjoner(personIdent: String): Personinfo {
        val uri = URI.create("$integrasjonerServiceUri/personopplysning/v1/info")
        logger.info("Henter personinfo fra $integrasjonerServiceUri")
        return try {
            val response = requestMedPersonIdent<Ressurs<Personinfo>>(uri, personIdent)
            secureLogger.info("Personinfo for {}: {}", personIdent, response?.body)
            response?.body?.data ?: throw RuntimeException("Response, body eller data er null.")
        } catch (e: RestClientException) {
            throw RuntimeException("Kall mot integrasjon feilet ved uthenting av personinfo. Exception=${e} Uri=${uri}")
        }
    }

    /*
    @Retryable(value = [IntegrasjonException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun oppdaterGosysOppgave(fnr: String?, journalpostID: String?, beskrivelse: String?) {
        val uri = URI.create("$integrasjonerServiceUri/oppgave/oppdater")
        logger.info("Sender \"oppdater oppgave\"-request til $uri")
        val oppgave = Oppgave(hentAktørId(fnr).getId(), journalpostID, null, beskrivelse)
        try {
            postRequest<T>(uri, OppgaveKt.toJson(oppgave), MutableMap::class.java)
        } catch (e: HttpClientErrorException.NotFound) {
            logger.warn("Oppgave returnerte 404, men kaster ikke feil. Uri: {}", uri)
        } catch (e: RestClientException) {
            throw IntegrasjonException("Kan ikke oppdater Gosys-oppgave", e, uri, oppgave.getAktorId())
        }
    }
    */
}