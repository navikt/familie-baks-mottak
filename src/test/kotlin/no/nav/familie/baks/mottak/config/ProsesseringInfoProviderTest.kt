package no.nav.familie.baks.mottak.config

import no.nav.familie.baks.mottak.config.security.ProsesseringInfoConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

/**
 * Enhetstester for ProsesseringInfoProvider.
 * Tester at bakoverkompatibilitetsbro for prosessering fungerer med ny Spring Security-arkitektur.
 */
class ProsesseringInfoProviderTest {
    private val prosesseringRolle = "test-prosessering-rolle"
    private val prosesseringInfoProvider =
        ProsesseringInfoConfig().prosesseringInfoProvider(prosesseringRolle)

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
    }

    private fun settOppSecurityContext(
        preferredUsername: String? = null,
        groups: List<String> = emptyList(),
    ) {
        val jwtBuilder =
            Jwt
                .withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "test-user")
                .claim("iss", "https://test-issuer")
                .claim("aud", "test-audience")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))

        if (preferredUsername != null) {
            jwtBuilder.claim("preferred_username", preferredUsername)
        }
        if (groups.isNotEmpty()) {
            jwtBuilder.claim("groups", groups)
        }

        val jwt = jwtBuilder.build()
        val authentication = JwtAuthenticationToken(jwt)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Nested
    inner class HentBrukernavn {
        @Test
        fun `skal returnere preferred_username fra JWT`() {
            settOppSecurityContext(preferredUsername = "test.bruker@nav.no")

            val brukernavn = prosesseringInfoProvider.hentBrukernavn()

            assertThat(brukernavn).isEqualTo("test.bruker@nav.no")
        }

        @Test
        fun `skal returnere VL når ingen JWT finnes`() {
            SecurityContextHolder.clearContext()

            val brukernavn = prosesseringInfoProvider.hentBrukernavn()

            assertThat(brukernavn).isEqualTo("VL")
        }

        @Test
        fun `skal returnere VL når JWT mangler preferred_username`() {
            settOppSecurityContext(preferredUsername = null, groups = emptyList())

            val brukernavn = prosesseringInfoProvider.hentBrukernavn()

            assertThat(brukernavn).isEqualTo("VL")
        }
    }

    @Nested
    inner class HarTilgang {
        @Test
        fun `skal returnere true når groups inneholder prosessering-rolle`() {
            settOppSecurityContext(groups = listOf(prosesseringRolle, "annen-rolle"))

            val harTilgang = prosesseringInfoProvider.harTilgang()

            assertThat(harTilgang).isTrue()
        }

        @Test
        fun `skal returnere false når groups ikke inneholder prosessering-rolle`() {
            settOppSecurityContext(groups = listOf("annen-rolle", "enda-en-rolle"))

            val harTilgang = prosesseringInfoProvider.harTilgang()

            assertThat(harTilgang).isFalse()
        }

        @Test
        fun `skal returnere false når groups mangler i JWT`() {
            settOppSecurityContext(groups = emptyList())

            val harTilgang = prosesseringInfoProvider.harTilgang()

            assertThat(harTilgang).isFalse()
        }

        @Test
        fun `skal returnere false når ingen JWT finnes`() {
            SecurityContextHolder.clearContext()

            val harTilgang = prosesseringInfoProvider.harTilgang()

            assertThat(harTilgang).isFalse()
        }
    }
}
