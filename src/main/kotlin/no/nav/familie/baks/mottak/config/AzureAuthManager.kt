package no.nav.familie.baks.mottak.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider
import org.springframework.stereotype.Component

@Component
class AzureAuthManager(
    @Value("\${AZURE_OPENID_CONFIG_ISSUER:}") private val azureIssuer: String,
    @Value("\${AZURE_APP_CLIENT_ID:}") private val azureClientId: String,
) : AuthenticationManager {

    override fun authenticate(authentication: Authentication): Authentication {
        val decoder = NimbusJwtDecoder.withIssuerLocation(azureIssuer).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(azureIssuer),
                JwtClaimValidator<Collection<String>>("aud") { audiences -> azureClientId in audiences },
            ),
        )
        return ProviderManager(JwtAuthenticationProvider(decoder)).authenticate(authentication)
    }

}
