package no.nav.familie.baks.mottak.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component

/**
 * AuthenticationManager som krever at JWT-token er utstedt av TokenX-issuer.
 * Brukes for å beskytte endepunkter som kun skal akseptere brukerautentisering via ID-porten (TokenX),
 * og ikke maskin-til-maskin tokens (Azure AD).
 */
@Component
class TokenXDecoder(
    @param:Value("\${TOKEN_X_ISSUER:}") private val tokenXIssuer: String,
    @param:Value("\${TOKEN_X_CLIENT_ID:}") private val tokenXClientId: String,
) : JwtDecoder {
    private val delegate by lazy {

        val decoder = NimbusJwtDecoder.withIssuerLocation(tokenXIssuer).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(tokenXIssuer),
                JwtClaimValidator<Collection<String>>("aud") { audiences -> tokenXClientId in audiences },
                JwtClaimValidator<String>("acr") { acr -> acr == "Level4" || acr == "idporten-loa-high" },
            ),
        )
        decoder
    }

    override fun decode(token: String?): Jwt? = delegate.decode(token)
}
