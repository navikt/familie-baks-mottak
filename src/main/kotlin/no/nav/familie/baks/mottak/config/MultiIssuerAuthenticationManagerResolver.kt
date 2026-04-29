package no.nav.familie.baks.mottak.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver
import org.springframework.stereotype.Component

@Component
class MultiIssuerAuthenticationManagerResolver(
    @param:Value("\${TOKEN_X_ISSUER:}") private val tokenXIssuer: String,
    @param:Value("\${TOKEN_X_CLIENT_ID:}") private val tokenXClientId: String,
    @param:Value("\${AZURE_OPENID_CONFIG_ISSUER:}") private val azureIssuer: String,
    @param:Value("\${AZURE_APP_CLIENT_ID:}") private val azureClientId: String,
) {
    companion object {
        val LEVEL4 = "Level4"
        val IDPORTEN_LOA_HIGH = "idporten-loa-high"
    }


    fun resolver(): JwtIssuerAuthenticationManagerResolver =
        JwtIssuerAuthenticationManagerResolver { issuer ->
            when (issuer.trimEnd('/')) {
                tokenXIssuer -> tokenXAuthManager
                azureIssuer -> azureAuthManager
                else -> throw BadCredentialsException("Unknown issuer: $issuer")
            }
        }

    private val tokenXAuthManager: AuthenticationManager by lazy {
        val decoder = NimbusJwtDecoder.withIssuerLocation(tokenXIssuer).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(tokenXIssuer),
                JwtClaimValidator<Collection<String>>("aud") { audiences -> tokenXClientId in audiences },
                JwtClaimValidator<String>("acr") { acr ->
                    acr == LEVEL4 || acr == IDPORTEN_LOA_HIGH
                },
            ),
        )
        ProviderManager(JwtAuthenticationProvider(decoder))
    }

    private val azureAuthManager: AuthenticationManager by lazy {
        val decoder = NimbusJwtDecoder.withIssuerLocation(azureIssuer).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(azureIssuer),
                JwtClaimValidator<Collection<String>>("aud") { audiences -> azureClientId in audiences },
            ),
        )
        ProviderManager(JwtAuthenticationProvider(decoder))
    }
}
