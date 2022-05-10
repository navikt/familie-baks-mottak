package no.nav.familie.ba.mottak.config

import no.nav.familie.http.config.NaisProxyCustomizer
import no.nav.familie.http.interceptor.ConsumerIdClientInterceptor
import no.nav.familie.http.interceptor.MdcValuesPropagatingClientInterceptor
import no.nav.familie.http.interceptor.StsBearerTokenClientInterceptor
import no.nav.familie.kontrakter.felles.objectMapper
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets

@Configuration
@Profile("dev")
@Import(ConsumerIdClientInterceptor::class,
        MdcValuesPropagatingClientInterceptor::class,
        StsBearerTokenClientInterceptor::class,
        NaisProxyCustomizer::class,)
class RestTemplateConfig {

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate(listOf(StringHttpMessageConverter(StandardCharsets.UTF_8), ByteArrayHttpMessageConverter()))
    }

    @Bean("jwtBearer")
    fun restTemplateJwtBearer(consumerIdClientInterceptor: ConsumerIdClientInterceptor,
                              mdcValuesPropagatingClientInterceptor: MdcValuesPropagatingClientInterceptor,
                              naisProxyCustomizer: NaisProxyCustomizer): RestOperations {

        return RestTemplateBuilder()
                .additionalCustomizers(naisProxyCustomizer)
                .additionalInterceptors(consumerIdClientInterceptor, mdcValuesPropagatingClientInterceptor)
                .build()
    }


    @Bean("clientCredentials")
    fun restTemplateClientCredentials(consumerIdClientInterceptor: ConsumerIdClientInterceptor,
                                      mdcValuesPropagatingClientInterceptor: MdcValuesPropagatingClientInterceptor,
                                      naisProxyCustomizer: NaisProxyCustomizer): RestOperations {
        return RestTemplateBuilder()
                .additionalCustomizers(naisProxyCustomizer)
                .additionalInterceptors(consumerIdClientInterceptor, mdcValuesPropagatingClientInterceptor)
                .additionalMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
                .build()
    }

    @Bean("restTemplateUnsecured")
    fun restTemplate(restTemplateBuilder: RestTemplateBuilder,
                     mdcInterceptor: MdcValuesPropagatingClientInterceptor,
                     consumerIdClientInterceptor: ConsumerIdClientInterceptor): RestOperations {
        return restTemplateBuilder.interceptors(mdcInterceptor, consumerIdClientInterceptor).build()
    }

    @Bean("sts")
    fun restTemplateSts(stsBearerTokenClientInterceptor: StsBearerTokenClientInterceptor,
                        consumerIdClientInterceptor: ConsumerIdClientInterceptor): RestOperations {

        return RestTemplateBuilder()
                .interceptors(consumerIdClientInterceptor,
                              stsBearerTokenClientInterceptor,
                              MdcValuesPropagatingClientInterceptor())
                .build()
    }
}
