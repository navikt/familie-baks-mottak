package no.nav.familie.baks.mottak.config

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.felles.tokenklient.entraid.EntraIDClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Configuration
@Profile("mock-oauth")
class EntraIDClientTestConfig {
    @Bean
    @Primary
    fun entraIDClientMock(): EntraIDClient {
        val mock = mockk<EntraIDClient>(relaxed = true)
        every { mock.hentMaskinTilMaskinToken(any()) } returns "mock-entra-id-token"
        return mock
    }
}
