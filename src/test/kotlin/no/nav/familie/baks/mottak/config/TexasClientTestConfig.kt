package no.nav.familie.baks.mottak.config

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.texas.TexasClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Configuration
@Profile("mock-oauth")
class TexasClientTestConfig {
    @Bean
    @Primary
    fun texasClientMock(): TexasClient {
        val mock = mockk<TexasClient>(relaxed = true)
        every { mock.hentMaskinToken(any()) } returns "mock-texas-token"
        return mock
    }
}
