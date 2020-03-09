package no.nav.familie.ba.mottak.config

import no.nav.familie.http.interceptor.ClientCredentialsInterceptor
import no.nav.familie.http.interceptor.ConsumerIdClientInterceptor
import no.nav.familie.http.interceptor.MdcValuesPropagatingClientInterceptor
import no.nav.familie.kontrakter.felles.objectMapper
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.client.RestOperations
import java.time.Duration

@Configuration
@Import(ConsumerIdClientInterceptor::class, ClientCredentialsInterceptor::class)
class RestTemplateConfig {

    @Profile("!dev")
    @Bean
    fun restTemplateBuilderMedProxy(): RestTemplateBuilder {
        return RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .additionalCustomizers(NaisProxyCustomizer())
                .additionalInterceptors(MdcValuesPropagatingClientInterceptor())
    }

    @Profile("dev")
    @Bean
    fun restTemplateBuilder(): RestTemplateBuilder {
        return RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
    }

    @Profile("!dev")
    @Bean("clientCredentials")
    fun restTemplateClientCredentials(consumerIdClientInterceptor: ConsumerIdClientInterceptor,
                                      clientCredentialsInterceptor: ClientCredentialsInterceptor): RestOperations {
        return RestTemplateBuilder()
            .additionalCustomizers(NaisProxyCustomizer())
            .interceptors(consumerIdClientInterceptor,
                clientCredentialsInterceptor,
                MdcValuesPropagatingClientInterceptor())
            .additionalMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
            .build()
    }
}
