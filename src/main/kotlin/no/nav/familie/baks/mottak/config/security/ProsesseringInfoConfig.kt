package no.nav.familie.baks.mottak.config.security

import no.nav.familie.prosessering.config.ProsesseringInfoProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

@Configuration
class ProsesseringInfoConfig {
    @Bean
    fun prosesseringInfoProvider(
        @Value("\${prosessering.rolle}") prosesseringRolle: String,
    ) = object : ProsesseringInfoProvider {
        override fun hentBrukernavn(): String =
            try {
                hentJwt()?.getClaimAsString("preferred_username") ?: "VL"
            } catch (e: Exception) {
                "VL"
            }

        override fun harTilgang(): Boolean = grupper().contains(prosesseringRolle)

        private fun grupper(): List<String> =
            try {
                hentJwt()?.getClaimAsStringList("groups") ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

        private fun hentJwt() = (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)?.token
    }
}
