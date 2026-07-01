package no.nav.familie.baks.mottak.config.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtAudienceValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component

@Component
class AzureDecoder(
    @param:Value("\${AZURE_OPENID_CONFIG_JWKS_URI}") private val azureJwksUri: String,
    @param:Value("\${AZURE_APP_CLIENT_ID}") private val azureClientId: String,
) : JwtDecoder {
    private val delegate =
        run {
            val decoder = NimbusJwtDecoder.withJwkSetUri(azureJwksUri).build()
            decoder.setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtValidators.createDefaultWithIssuer(azureJwksUri),
                    JwtAudienceValidator(azureClientId),
                ),
            )

            decoder
        }

    override fun decode(token: String): Jwt = delegate.decode(token)
}
