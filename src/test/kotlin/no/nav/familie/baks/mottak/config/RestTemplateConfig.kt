package no.nav.familie.baks.mottak.config

import no.nav.familie.restklient.config.jsonMapper
import no.nav.familie.restklient.interceptor.ConsumerIdClientInterceptor
import no.nav.familie.restklient.interceptor.MdcValuesPropagatingClientInterceptor
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets

@Configuration(enforceUniqueMethods = false)
@Profile("dev", "postgres")
@Import(
    ConsumerIdClientInterceptor::class,
    MdcValuesPropagatingClientInterceptor::class,
)
class RestTemplateConfig {
    @Bean
    fun restTemplate(): RestTemplate = RestTemplate(listOf(StringHttpMessageConverter(StandardCharsets.UTF_8), ByteArrayHttpMessageConverter()))

    @Bean("jwtBearer")
    fun restTemplateJwtBearer(
        consumerIdClientInterceptor: ConsumerIdClientInterceptor,
        mdcValuesPropagatingClientInterceptor: MdcValuesPropagatingClientInterceptor,
    ): RestOperations =
        RestTemplateBuilder()
            .additionalInterceptors(consumerIdClientInterceptor, mdcValuesPropagatingClientInterceptor)
            .build()

    @Bean("clientCredentials")
    fun restTemplateClientCredentials(
        consumerIdClientInterceptor: ConsumerIdClientInterceptor,
        mdcValuesPropagatingClientInterceptor: MdcValuesPropagatingClientInterceptor,
    ): RestOperations =
        RestTemplateBuilder()
            .additionalInterceptors(consumerIdClientInterceptor, mdcValuesPropagatingClientInterceptor)
            .additionalMessageConverters(JacksonJsonHttpMessageConverter(jsonMapper))
            .build()

    @Bean("restTemplateUnsecured")
    fun restTemplate(
        restTemplateBuilder: RestTemplateBuilder,
        mdcInterceptor: MdcValuesPropagatingClientInterceptor,
        consumerIdClientInterceptor: ConsumerIdClientInterceptor,
    ): RestOperations = restTemplateBuilder.interceptors(mdcInterceptor, consumerIdClientInterceptor).build()
}
