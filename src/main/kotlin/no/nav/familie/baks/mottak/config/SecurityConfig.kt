package no.nav.familie.baks.mottak.config

import no.nav.familie.prosessering.config.ProsesseringInfoProvider
import no.nav.familie.sikkerhet.context.FamilieFellesSpringSecurityKonfigurasjon
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtToken
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered.LOWEST_PRECEDENCE
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
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
    @Order(LOWEST_PRECEDENCE)
    fun defaultSecurityFilterChain(
        http: HttpSecurity,
    ): SecurityFilterChain {
        http {
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
        // Ekskluder /api/task/** slik at prosessering-web-spring-security håndterer det
        http.securityMatcher(NegatedRequestMatcher(PathPatternRequestMatcher.pathPattern("/api/task/**")))
        return http.build()
    }

    @Bean
    fun prosesseringInfoProvider(
        @Value("\${prosessering.rolle}") prosesseringRolle: String,
    ) = object : ProsesseringInfoProvider {
        override fun hentBrukernavn(): String =
            try {
                hentJwt()?.getClaimAsString("preferred_username") ?: "VL"
            } catch (e: Exception) {
                "VL"
            }

        override fun harTilgang(): Boolean = grupper().contains(prosesseringRolle)

        private fun grupper(): List<String> =
            try {
                hentJwt()?.getClaimAsStringList("groups") ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

        private fun hentJwt() = (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)?.token
    }
}
