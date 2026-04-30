package no.nav.familie.baks.mottak.config

import no.nav.familie.baks.mottak.texas.TexasRestClientFactory
import no.nav.familie.restklient.interceptor.ConsumerIdClientInterceptor
import no.nav.familie.restklient.interceptor.MdcValuesPropagatingClientInterceptor
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
    private val texasRestClientFactory: TexasRestClientFactory,
    private val consumerIdClientInterceptor: ConsumerIdClientInterceptor,
    private val mdcValuesPropagatingClientInterceptor: MdcValuesPropagatingClientInterceptor,
) {
    @Bean("integrasjonerRestClient")
    fun integrasjonerRestClient(
        @Value("\${FAMILIE_INTEGRASJONER_SCOPE}") scope: String,
    ): RestClient = texasRestClientFactory.lagMaskinRestKlient(scope)

    @Bean("unauthenticatedRestClient")
    fun unauthenticatedRestClient(): RestClient =
        RestClient
            .builder()
            .requestInterceptors {
                listOf(
                    consumerIdClientInterceptor,
                    mdcValuesPropagatingClientInterceptor,
                )
            }.build()
}
