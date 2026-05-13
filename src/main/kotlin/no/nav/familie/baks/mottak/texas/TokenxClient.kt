package no.nav.familie.baks.mottak.texas

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class TokenxClient(
    @param:Value("\${NAIS_TOKEN_EXCHANGE_ENDPOINT}") private val tokenEndpoint: String,
) {
    private val restClient = RestClient.create()

    fun hentToken(scope: String): String {
        val authentication = SecurityContextHolder.getContext().authentication

        val token =
            if (authentication is JwtAuthenticationToken) {
                authentication.token.tokenValue
            } else {
                throw IllegalStateException("Finner ikke brukertoken i security context")
            }

        val body =
            mapOf(
                "identity_provider" to "tokenx",
                "target" to scope,
                "user_token" to token,
            )
        val response =
            restClient
                .post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(TexasTokenResponse::class.java)
                ?: throw IllegalStateException("Fikk ikke svar fra Texas")

        return response.accessToken
    }
}
