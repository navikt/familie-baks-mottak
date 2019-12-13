package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.domene.personopplysning.Personinfo
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.log.NavHttpHeaders
import no.nav.familie.log.mdc.MDCConstants
import no.nav.familie.sikkerhet.OIDCUtil
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClientException
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val logger = LoggerFactory.getLogger(PersonService::class.java)
private val secureLogger = LoggerFactory.getLogger("secureLogger")
private const val OAUTH2_CLIENT_CONFIG_KEY = "integrasjoner-clientcredentials"

@Component
class PersonService @Autowired constructor(@param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonerServiceUri: URI,
                                                 restTemplateBuilderMedProxy: RestTemplateBuilder?,
                                                 clientConfigurationProperties: ClientConfigurationProperties?,
                                                 oAuth2AccessTokenService: OAuth2AccessTokenService?,
                                                 private val oidcUtil: OIDCUtil) : BaseService(OAUTH2_CLIENT_CONFIG_KEY, restTemplateBuilderMedProxy!!, clientConfigurationProperties!!, oAuth2AccessTokenService!!) {

    private inline fun <reified T, U> request(uri: URI, method: HttpMethod, httpEntity: HttpEntity<U>) : ResponseEntity<T>? {
        val ressursResponse = restTemplate.exchange(uri, method, httpEntity, T::class.java)

        if (ressursResponse.getBody() == null) {
            throw RuntimeException("Response kan ikke være tom: " + uri)
        }
        return ResponseEntity.status(ressursResponse.getStatusCode()).body(ressursResponse.getBody())
    }

    private inline fun <reified T> request(uri: URI): ResponseEntity<T>? {
        val headers: MultiValueMap<String, String> = LinkedMultiValueMap()
        headers.add(NavHttpHeaders.NAV_CALL_ID.asString(), MDC.get(MDCConstants.MDC_CALL_ID))
        val httpEntity: HttpEntity<*> = HttpEntity<Any?>(headers)
        return request(uri, HttpMethod.GET, httpEntity)
    }

    private inline fun <reified T, U> postRequest(uri: URI, requestBody: U): ResponseEntity<T>? {
        val headers = HttpHeaders()
        headers.add("Content-Type", "application/json;charset=UTF-8")
        headers.add(NavHttpHeaders.NAV_CALL_ID.asString(), MDC.get(MDCConstants.MDC_CALL_ID))
        val httpEntity: HttpEntity<U> = HttpEntity(requestBody, headers)
        return request(uri, HttpMethod.POST, httpEntity)
    }

    private inline fun <reified T> requestMedPersonIdent(uri: URI, personident: String): ResponseEntity<T>? {
        val headers: MultiValueMap<String, String> = LinkedMultiValueMap()
        headers.add(NavHttpHeaders.NAV_CALL_ID.asString(), MDC.get(MDCConstants.MDC_CALL_ID))
        headers.add(NavHttpHeaders.NAV_PERSONIDENT.asString(), personident)
        val httpEntity: HttpEntity<*> = HttpEntity<Any?>(headers)
        return request(uri, HttpMethod.GET, httpEntity)
    }

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun hentPersonMedRelasjoner(personIdent: String): Personinfo {
        val uri = URI.create("$integrasjonerServiceUri/personopplysning/v1/info")
        logger.info("Henter personinfo fra $integrasjonerServiceUri")
        return try {
            val response = requestMedPersonIdent<Ressurs<Personinfo>>(uri, personIdent)
            secureLogger.info("Personinfo for {}: {}", personIdent, response?.body)
            response!!.body!!.data!!
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
    private fun formaterDato(date: LocalDate): String {
        return date.format(DateTimeFormatter.ISO_DATE)
    }
}