package no.nav.familie.ba.mottak.config

import org.springframework.boot.SpringBootConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@SpringBootConfiguration
class ApplicationConfig {

    @Bean
    @Profile("dev")
    @Primary
    fun vaultServiceUserMock(): VaultServiceUser {
        return VaultServiceUser(
                serviceuserUsername = "not-a-real-srvuser",
                serviceuserPassword = "not-a-real-pw")
    }
}