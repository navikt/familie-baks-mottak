package no.nav.familie.baks.mottak.config

import no.nav.security.mock.oauth2.MockOAuth2Server

/**
 * Hjelpeklasse for å generere JWT-tokens i tester.
 * Bruker MockOAuth2Server til å generere ekte JWT-tokens med tilpassbare claims.
 */
object JwtTokenTestUtil {
    /**
     * Generer TokenX-token med tilpassbare claims.
     * TokenX-tokens brukes for brukerautentisering via ID-porten.
     */
    fun lagTokenXToken(
        mockOAuth2Server: MockOAuth2Server,
        subject: String = "12345678901",
        audience: String = "aud-localhost",
        acr: String = "Level4",
        tilleggsClaims: Map<String, Any> = emptyMap(),
    ): String {
        val claims =
            mapOf(
                "acr" to acr,
                "pid" to subject,
            ) + tilleggsClaims

        return mockOAuth2Server
            .issueToken(
                issuerId = "tokenx",
                subject = subject,
                audience = audience,
                claims = claims,
            ).serialize()
    }

    /**
     * Generer Azure AD-token med tilpassbare claims.
     * Azure AD-tokens brukes for maskin-til-maskin og ansatt-autentisering.
     */
    fun lagAzureAdToken(
        mockOAuth2Server: MockOAuth2Server,
        subject: String = "test-user@nav.no",
        audience: String = "aud-localhost",
        groups: List<String> = emptyList(),
        preferredUsername: String = "test-user@nav.no",
        tilleggsClaims: Map<String, Any> = emptyMap(),
    ): String {
        val claims =
            mapOf(
                "groups" to groups,
                "preferred_username" to preferredUsername,
                "name" to preferredUsername,
            ) + tilleggsClaims

        return mockOAuth2Server
            .issueToken(
                issuerId = "azuread",
                subject = subject,
                audience = audience,
                claims = claims,
            ).serialize()
    }

    /**
     * Generer ugyldig token for negative tester.
     */
    fun lagUgyldigToken(
        mockOAuth2Server: MockOAuth2Server,
        årsak: UgyldigTokenÅrsak,
    ): String =
        when (årsak) {
            UgyldigTokenÅrsak.FEIL_ISSUER -> {
                mockOAuth2Server
                    .issueToken(
                        issuerId = "ukjent-issuer",
                        subject = "test",
                        audience = "aud-localhost",
                    ).serialize()
            }

            UgyldigTokenÅrsak.FEIL_AUDIENCE -> {
                mockOAuth2Server
                    .issueToken(
                        issuerId = "tokenx",
                        subject = "12345678901",
                        audience = "feil-audience",
                        claims = mapOf("acr" to "Level4"),
                    ).serialize()
            }

            UgyldigTokenÅrsak.FEIL_ACR -> {
                mockOAuth2Server
                    .issueToken(
                        issuerId = "tokenx",
                        subject = "12345678901",
                        audience = "aud-localhost",
                        claims = mapOf("acr" to "Level3"),
                    ).serialize()
            }

            UgyldigTokenÅrsak.UTGÅTT -> {
                mockOAuth2Server
                    .issueToken(
                        issuerId = "tokenx",
                        subject = "12345678901",
                        audience = "aud-localhost",
                        claims = mapOf("acr" to "Level4"),
                        expiry = -3600L,
                    ).serialize()
            }
        }

    enum class UgyldigTokenÅrsak {
        FEIL_ISSUER,
        FEIL_AUDIENCE,
        FEIL_ACR,
        UTGÅTT,
    }
}
