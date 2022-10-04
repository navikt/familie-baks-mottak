package no.nav.familie.ba.mottak.config

import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Configuration
@Profile("mock-oauth")
class OAuth2AccessTokenTestConfig {

    @Bean
    @Primary
    fun oAuth2AccessTokenServiceMock(): OAuth2AccessTokenService? {
        val tokenMockService =
            Mockito.mock(OAuth2AccessTokenService::class.java)
        Mockito.`when`(tokenMockService.getAccessToken(ArgumentMatchers.any()))
            .thenReturn(OAuth2AccessTokenResponse("Mock-token-response", 60, 60, null))
        return tokenMockService
    }
}
