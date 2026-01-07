package no.nav.familie.baks.mottak.config

import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.restklient.interceptor.BearerTokenClientInterceptor
import no.nav.familie.restklient.interceptor.ConsumerIdClientInterceptor
import no.nav.familie.restklient.interceptor.MdcValuesPropagatingClientInterceptor
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.client.RestOperations

@Configuration
@Import(
    BearerTokenClientInterceptor::class,
    MdcValuesPropagatingClientInterceptor::class,
    ConsumerIdClientInterceptor::class,
)
class RestTemplateConfig {
    @Profile("!dev || !postgres")
    @Bean("clientCredentials")
    fun restTemplateClientCredentials(
        consumerIdClientInterceptor: ConsumerIdClientInterceptor,
        bearerTokenClientInterceptor: BearerTokenClientInterceptor,
    ): RestOperations =
        RestTemplateBuilder()
            .interceptors(
                consumerIdClientInterceptor,
                bearerTokenClientInterceptor,
                MdcValuesPropagatingClientInterceptor(),
            ).additionalMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
            .build()

    @Bean("restTemplateUnsecured")
    fun restTemplate(
        restTemplateBuilder: RestTemplateBuilder,
        mdcInterceptor: MdcValuesPropagatingClientInterceptor,
        consumerIdClientInterceptor: ConsumerIdClientInterceptor,
    ): RestOperations = restTemplateBuilder.interceptors(mdcInterceptor, consumerIdClientInterceptor).build()
}
