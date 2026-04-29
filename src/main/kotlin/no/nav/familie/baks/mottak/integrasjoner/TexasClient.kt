package no.nav.familie.baks.mottak.integrasjoner

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class TexasClient(
    @param:Value("\${NAIS_TOKEN_ENDPOINT}") private val tokenEndpoint: String,
) {
    private val restClient = RestClient.create()

    fun lagSystemToken(target: String): String {
        val body =
            mapOf(
                "identity_provider" to "entra_id",
                "target" to target,
            )
        val response =
            restClient
                .post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(TexasTokenResponse::class.java)
                ?: throw IllegalStateException("Fikk ikke svar fra Texas (NAIS_TOKEN_ENDPOINT)")
        return response.accessToken
    }
}
