package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.log.NavHttpHeaders
import no.nav.familie.log.mdc.MDCConstants
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.slf4j.MDC
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.*
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestOperations
import org.springframework.web.client.exchange
import java.net.URI

class BearerAuthorizationInterceptor(private val oAuth2AccessTokenService: OAuth2AccessTokenService, private val clientProperties: ClientProperties) : ClientHttpRequestInterceptor {
    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)

        request.headers.setBearerAuth(response.accessToken)
        return execution.execute(request, body)
    }
}

open class BaseService(clientConfigKey: String, restTemplateBuilder: RestTemplateBuilder,
                       clientConfigurationProperties: ClientConfigurationProperties,
                       oAuth2AccessTokenService: OAuth2AccessTokenService) {

    private val clientProperties: ClientProperties = clientConfigurationProperties.registration[clientConfigKey] ?:
            throw RuntimeException("could not find oauth2 client config for key=$clientConfigKey")

    val restOperations: RestOperations = restTemplateBuilder
            .additionalInterceptors(BearerAuthorizationInterceptor(oAuth2AccessTokenService, clientProperties))
            .build()

    protected inline fun <reified T> getRequest(uri: URI): ResponseEntity<T>? {
        val headers: MultiValueMap<String, String> = LinkedMultiValueMap()
        headers.add(NavHttpHeaders.NAV_CALL_ID.asString(), MDC.get(MDCConstants.MDC_CALL_ID))
        val httpEntity: HttpEntity<*> = HttpEntity<Any?>(headers)
        return restOperations.exchange<T>(uri, HttpMethod.GET, httpEntity)
    }

    protected inline fun <reified T, U> postRequest(uri: URI, requestBody: U): ResponseEntity<T>? {
        val headers = HttpHeaders()
        headers.add("Content-Type", "application/json;charset=UTF-8")
        headers.add(NavHttpHeaders.NAV_CALL_ID.asString(), MDC.get(MDCConstants.MDC_CALL_ID))
        val httpEntity: HttpEntity<U> = HttpEntity(requestBody, headers)
        return restOperations.exchange<T>(uri, HttpMethod.POST, httpEntity)
    }

    protected inline fun <reified T> requestMedPersonIdent(uri: URI, personident: String): ResponseEntity<T>? {
        val headers: MultiValueMap<String, String> = LinkedMultiValueMap()
        headers.add(NavHttpHeaders.NAV_CALL_ID.asString(), MDC.get(MDCConstants.MDC_CALL_ID))
        headers.add(NavHttpHeaders.NAV_PERSONIDENT.asString(), personident)
        val httpEntity: HttpEntity<*> = HttpEntity<Any?>(headers)
        return restOperations.exchange<T>(uri, HttpMethod.GET, httpEntity)
    }
}
