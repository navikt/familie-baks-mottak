package no.nav.familie.baks.mottak.integrasjoner

import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.net.URI

@Component
class AuthorizedRestClient(
    private val texasClient: TexasClient,
) {
    private val restTemplate = RestTemplate()

    private fun lagHeaders(target: String): HttpHeaders =
        HttpHeaders().apply {
            setBearerAuth(texasClient.lagSystemToken(target))
            contentType = MediaType.APPLICATION_JSON
        }

    fun <T : Any> post(
        uri: URI,
        body: Any,
        target: String,
        responseType: ParameterizedTypeReference<T>,
    ): T =
        restTemplate
            .exchange(uri.toString(), HttpMethod.POST, HttpEntity(body, lagHeaders(target)), responseType)
            .body
            ?: throw IllegalStateException("Tom respons fra $uri")

    fun <T : Any> put(
        uri: URI,
        body: Any,
        target: String,
        responseType: ParameterizedTypeReference<T>,
    ): T =
        restTemplate
            .exchange(uri.toString(), HttpMethod.PUT, HttpEntity(body, lagHeaders(target)), responseType)
            .body
            ?: throw IllegalStateException("Tom respons fra $uri")

    fun <T : Any> get(
        uri: URI,
        target: String,
        responseType: ParameterizedTypeReference<T>,
    ): T =
        restTemplate
            .exchange(uri.toString(), HttpMethod.GET, HttpEntity<Void>(lagHeaders(target)), responseType)
            .body
            ?: throw IllegalStateException("Tom respons fra $uri")
}
