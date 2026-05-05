package no.nav.familie.baks.mottak.config

import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.authorization.AuthorizationResult
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import java.util.function.Supplier

/**
 * AuthorizationManager som krever at JWT-token er utstedt av TokenX-issuer.
 * Brukes for å beskytte endepunkter som kun skal akseptere brukerautentisering via ID-porten (TokenX),
 * og ikke maskin-til-maskin tokens (Azure AD).
 */
class TokenXAuthorizationManager(
    private val tokenXIssuer: String,
) : AuthorizationManager<RequestAuthorizationContext> {
    override fun authorize(
        authentication: Supplier<out Authentication?>,
        context: RequestAuthorizationContext,
    ): AuthorizationResult {
        val auth = authentication.get()

        if (auth !is JwtAuthenticationToken) {
            return AuthorizationDecision(false)
        }

        val issuer =
            auth.token.issuer
                ?.toString()
                ?.trimEnd('/')
        return AuthorizationDecision(issuer == tokenXIssuer)
    }
}
