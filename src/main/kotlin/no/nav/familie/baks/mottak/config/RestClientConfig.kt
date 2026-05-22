package no.nav.familie.baks.mottak.config

import no.nav.familie.felles.tokenklient.entraid.EntraIDRestClientFactory
import no.nav.familie.felles.tokenklient.tokenx.TokenXClient
import no.nav.familie.felles.tokenklient.tokenx.TokenXInterceptor
import no.nav.familie.log.interceptor.ConsumerIdClientInterceptor
import no.nav.familie.log.interceptor.MdcValuesPropagatingClientInterceptor
import no.nav.familie.sikkerhet.EksternBrukerUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient

@Configuration
@Import(
    ConsumerIdClientInterceptor::class,
    MdcValuesPropagatingClientInterceptor::class,
)
class RestClientConfig(
    private val entraIDRestClientFactory: EntraIDRestClientFactory,
    private val consumerIdClientInterceptor: ConsumerIdClientInterceptor,
    private val mdcValuesPropagatingClientInterceptor: MdcValuesPropagatingClientInterceptor,
) {
    @Bean("integrasjonerRestClient")
    fun integrasjonerRestClient(
        @Value("\${FAMILIE_INTEGRASJONER_SCOPE}") scope: String,
    ): RestClient = entraIDRestClientFactory.lagMaskinTilMaskinRestKlient(scope)

    @Bean("unauthenticatedRestClient")
    fun unauthenticatedRestClient(): RestClient =
        RestClient
            .builder()
            .requestInterceptor(consumerIdClientInterceptor)
            .requestInterceptor(mdcValuesPropagatingClientInterceptor)
            .build()

    @Bean("familieTokenXRestClient")
    fun familieTokenXRestClient(
        tokenxClient: TokenXClient,
        @Value("\${FAMILIE_DOKUMENT_SCOPE}") scope: String,
    ): RestClient =
        RestClient
            .builder()
            .requestInterceptor(
                TokenXInterceptor(tokenxClient, scope) {
                    EksternBrukerUtils.getBearerTokenForLoggedInUser()
                },
            ).requestInterceptor(consumerIdClientInterceptor)
            .requestInterceptor(mdcValuesPropagatingClientInterceptor)
            .build()
}
