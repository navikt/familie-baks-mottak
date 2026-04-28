package no.nav.familie.baks.mottak.integrasjoner

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class TexasClient(
    @param:Value("\${NAIS_TOKEN_ENDPOINT}") private val tokenEndpoint: String,
) {
    private val restTemplate = RestTemplate()

    fun lagSystemToken(target: String): String {
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
        val body =
            mapOf(
                "identity_provider" to "entra_id",
                "target" to target,
            )
        val response =
            restTemplate.postForObject(
                tokenEndpoint,
                HttpEntity(body, headers),
                TexasTokenResponse::class.java,
            ) ?: throw IllegalStateException("Fikk ikke svar fra Texas (NAIS_TOKEN_ENDPOINT)")
        return response.accessToken
    }
}
