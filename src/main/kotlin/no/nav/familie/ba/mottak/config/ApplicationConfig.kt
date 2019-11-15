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
        val vaultServiceUser = VaultServiceUser(
                serviceuserUsername = getFileAsString("/secrets/srvfamilie-ba-mottak/username"),
                serviceuserPassword = getFileAsString("/secrets/srvfamilie-ba-mottak/password"))
        return vaultServiceUser
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
}