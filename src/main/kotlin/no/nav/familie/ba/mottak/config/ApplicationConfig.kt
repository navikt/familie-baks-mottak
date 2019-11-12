package no.nav.familie.ba.mottak.config

import org.springframework.boot.SpringBootConfiguration
import org.springframework.context.annotation.Bean
import java.io.IOException
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

@SpringBootConfiguration
class ApplicationConfig {

    @Bean
    fun vaultServiceUser(): VaultServiceUser {
        return VaultServiceUser(
                serviceuserPassword = getFileAsString("/secrets/srvfamilie-ba-mottak/password"),
                serviceuserUsername = getFileAsString("/secrets/srvfamilie-ba-mottak/username"))
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
}