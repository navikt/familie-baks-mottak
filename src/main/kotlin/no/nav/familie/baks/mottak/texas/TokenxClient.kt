package no.nav.familie.baks.mottak.texas

import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.prosessering.internal.TaskScheduler.Companion.secureLog
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.readValue

@Component
class TokenxClient(
    @param:Value("\${NAIS_TOKEN_INTROSPECTION_ENDPOINT}") private val tokenEndpoint: String,
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
                "token" to token,
            )
        val response =
            restClient
                .post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String::class.java)
                ?: throw IllegalStateException("Fikk ikke svar fra Texas")
        secureLog.error("Hentet token fra Texas for scope $scope, $token, $response, $tokenEndpoint")

        val tokenResponse: TexasTokenResponse = jsonMapper.readValue(response)

        return tokenResponse.accessToken
    }
}
