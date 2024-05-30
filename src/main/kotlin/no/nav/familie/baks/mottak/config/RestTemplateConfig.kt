package no.nav.familie.baks.mottak.config

import no.nav.familie.http.config.RestTemplateSts
import no.nav.familie.http.interceptor.BearerTokenClientInterceptor
import no.nav.familie.http.interceptor.ConsumerIdClientInterceptor
import no.nav.familie.http.interceptor.MdcValuesPropagatingClientInterceptor
import no.nav.familie.kontrakter.felles.objectMapper
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.client.RestOperations

@Configuration
@Import(
    BearerTokenClientInterceptor::class,
    MdcValuesPropagatingClientInterceptor::class,
    RestTemplateSts::class,
)
class RestTemplateConfig {
    @Profile("!dev || !postgres")
    @Bean("clientCredentials")
    fun restTemplateClientCredentials(
        consumerIdClientInterceptor: ConsumerIdClientInterceptor,
        bearerTokenClientInterceptor: BearerTokenClientInterceptor,
    ): RestOperations {
        return RestTemplateBuilder()
            .interceptors(
                consumerIdClientInterceptor,
                bearerTokenClientInterceptor,
                MdcValuesPropagatingClientInterceptor(),
            )
            .additionalMessageConverters(MappingJackson2HttpMessageConverter(objectMapper), ByteArrayHttpMessageConverter())
            .build()
    }

    @Bean("restTemplateUnsecured")
    fun restTemplate(
        restTemplateBuilder: RestTemplateBuilder,
        mdcInterceptor: MdcValuesPropagatingClientInterceptor,
        consumerIdClientInterceptor: ConsumerIdClientInterceptor,
    ): RestOperations {
        return restTemplateBuilder.interceptors(mdcInterceptor, consumerIdClientInterceptor).build()
    }
}
