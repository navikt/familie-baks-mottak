package no.nav.familie.baks.mottak.integrasjoner

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class TexasRestClientFactory(
    private val texasClient: TexasClient,
) {
    fun create(target: String): RestClient =
        RestClient
            .builder()
            .requestInterceptor { request, body, execution ->
                request.headers.setBearerAuth(texasClient.lagSystemToken(target))
                execution.execute(request, body)
            }.build()
}
