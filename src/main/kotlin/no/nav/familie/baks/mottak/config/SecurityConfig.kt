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
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Import(FamilieFellesSpringSecurityKonfigurasjon::class)
class SecurityConfig(
    private val azureAuthManager: AzureAuthManager,
) {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        tokenXAuthorizationManager: AuthorizationManager<RequestAuthorizationContext>,
    ): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/internal/**", permitAll)
                authorize("/actuator/**", permitAll)
                authorize("/api/ping", permitAll)
                authorize("/api/kontantstotte/ping", permitAll)
                authorize("/api/status/**", permitAll)

                // Krev TokenX for kontantstøtte-søknader
                authorize("/api/kontantstotte/soknad/**", tokenXAuthorizationManager)

                // Krev TokenX for barnetrygd-søknader
                authorize("/api/soknad/**", tokenXAuthorizationManager)

                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                jwt { authenticationManager = azureAuthManager }
            }
            csrf { disable() }
        }
        // Ekskluder /api/task/** slik at prosessering-web-spring-security håndterer det
        http.securityMatcher(NegatedRequestMatcher(PathPatternRequestMatcher.pathPattern("/api/task/**")))
        return http.build()
    }


    @Bean
    fun tokenXAuthorizationManager(
        @Value("\${TOKEN_X_ISSUER:}") tokenXIssuer: String,
    ): AuthorizationManager<RequestAuthorizationContext> = TokenXAuthorizationManager(tokenXIssuer)


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

    @Bean
    fun tokenValidationContextHolder(): TokenValidationContextHolder = SpringSecurityTokenValidationContextHolder()
}

class SpringSecurityTokenValidationContextHolder : TokenValidationContextHolder {
    override fun getTokenValidationContext(): TokenValidationContext {
        val jwt = (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)?.token
        val validatedTokens = jwt?.issuer?.let { mapOf(it.toString() to JwtToken(jwt.tokenValue)) } ?: emptyMap()
        return TokenValidationContext(validatedTokens)
    }

    override fun setTokenValidationContext(tokenValidationContext: TokenValidationContext?) {
        // No-op: Spring Security manages the token context
    }
}
