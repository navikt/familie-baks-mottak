package no.nav.familie.baks.mottak.config.security

import no.nav.familie.baks.mottak.config.security.AzureDecoder
import no.nav.familie.baks.mottak.config.security.TokenXDecoder
import no.nav.familie.sikkerhet.context.FamilieFellesSpringSecurityKonfigurasjon
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Import(FamilieFellesSpringSecurityKonfigurasjon::class)
class SecurityConfig(
    private val azureDecoder: AzureDecoder,
    private val tokenXDecoder: TokenXDecoder,
) {
    @Bean
    @Order(1)
    fun søknadSecurityFilterChain(
        http: HttpSecurity,
    ): SecurityFilterChain {
        http {
            securityMatcher("/api/soknad/**", "/api/kontantstotte/soknad/**")

            authorizeHttpRequests {
                authorize(anyRequest, authenticated)
            }

            oauth2ResourceServer {
                jwt { jwtDecoder = tokenXDecoder }
            }
            csrf { disable() }
        }

        return http.build()
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun defaultSecurityFilterChain(
        http: HttpSecurity,
    ): SecurityFilterChain {
        http {
            // Ekskluder /api/task/** for å garantere at prosessering-web-spring-security håndterer det
            http.securityMatcher(NegatedRequestMatcher(PathPatternRequestMatcher.pathPattern("/api/task/**")))

            authorizeHttpRequests {
                authorize("/internal/**", permitAll)
                authorize("/api/ping", permitAll)
                authorize("/api/kontantstotte/ping", permitAll)
                authorize("/api/status/barnetrygd", permitAll)
                authorize("/api/status/kontantstotte", permitAll)

                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                jwt { jwtDecoder = azureDecoder }
            }
            csrf { disable() }
        }

        return http.build()
    }
}
