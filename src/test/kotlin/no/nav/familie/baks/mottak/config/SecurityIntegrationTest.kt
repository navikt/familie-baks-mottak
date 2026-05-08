package no.nav.familie.baks.mottak.config

import no.nav.familie.baks.mottak.DevLauncher
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.assertFalse

/**
 * Integrasjonstester for sikkerhetskonfigurasjon.
 * Tester autentisering og autorisasjon med TokenX og Azure AD.
 */
@SpringBootTest(
    classes = [DevLauncher::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("dev")
@ContextConfiguration(initializers = [MockOAuth2ServerInitializer::class])
class SecurityIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var mockOAuth2Server: MockOAuth2Server

    private lateinit var restTestClient: RestTestClient

    @BeforeEach
    fun setup() {
        restTestClient =
            RestTestClient
                .bindToServer()
                .baseUrl("http://localhost:$port")
                .build()
    }

    @Nested
    inner class OffentligeEndepunkter {
        @Test
        fun `skal tillate tilgang til internal-endepunkter uten token`() {
            restTestClient
                .get()
                .uri("/internal/health")
                .exchange()
                .expectStatus()
                .isOk
        }

        @Test
        fun `skal tillate tilgang til api ping uten token`() {
            restTestClient
                .get()
                .uri("/api/ping")
                .exchange()
                .expectStatus()
                .isOk
        }

        @Test
        fun `skal tillate tilgang til kontantstøtte ping uten token`() {
            restTestClient
                .get()
                .uri("/api/kontantstotte/ping")
                .exchange()
                .expectStatus()
                .isOk
        }

        @Test
        fun `skal tillate tilgang til status barnetrygd uten token`() {
            val status =
                restTestClient
                    .get()
                    .uri("/api/status/barnetrygd")
                    .exchange()
                    .returnResult()
                    .status
            assertFalse(status.is4xxClientError)
        }

        @Test
        fun `skal tillate tilgang til status kontantstøtte uten token`() {
            val status =
                restTestClient
                    .get()
                    .uri("/api/status/kontantstotte")
                    .exchange()
                    .returnResult()
                    .status
            assertFalse(status.is4xxClientError)
        }
    }

    @Nested
    inner class TokenXSøknadsendepunkter {
        @Test
        fun `skal avvise søknad uten token`() {
            restTestClient
                .post()
                .uri("/api/soknad/v10")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }

        @Test
        fun `skal avvise kontantstøtte søknad uten token`() {
            restTestClient
                .post()
                .uri("/api/kontantstotte/soknad/v6")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }

        @Test
        fun `skal akseptere TokenX-token med Level4 for barnetrygd-søknad`() {
            val token = JwtTokenTestUtil.lagTokenXToken(mockOAuth2Server, acr = "Level4")

            val status =
                restTestClient
                    .post()
                    .uri("/api/soknad/v10")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .exchange()
                    .returnResult()
                    .status
            assertFalse(status.is4xxClientError, "status var $status, forventet ikke 4xx")
        }

        @Test
        fun `skal akseptere TokenX-token med idporten-loa-high for barnetrygd-søknad`() {
            val token = JwtTokenTestUtil.lagTokenXToken(mockOAuth2Server, acr = "idporten-loa-high")

            val status =
                restTestClient
                    .post()
                    .uri("/api/soknad/v10")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .exchange()
                    .returnResult()
                    .status
            assertFalse(status.is4xxClientError, "status var $status, forventet ikke 4xx")
        }

        @Test
        fun `skal akseptere TokenX-token med Level4 for kontantstøtte-søknad`() {
            val token = JwtTokenTestUtil.lagTokenXToken(mockOAuth2Server, acr = "Level4")

            val status =
                restTestClient
                    .post()
                    .uri("/api/kontantstotte/soknad/v6")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .exchange()
                    .returnResult()
                    .status
            assertFalse(status.is4xxClientError, "status var $status, forventet ikke 4xx")
        }

        @Test
        fun `skal akseptere TokenX-token med idporten-loa-high for kontantstøtte-søknad`() {
            val token = JwtTokenTestUtil.lagTokenXToken(mockOAuth2Server, acr = "idporten-loa-high")

            val status =
                restTestClient
                    .post()
                    .uri("/api/kontantstotte/soknad/v6")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .exchange()
                    .returnResult()
                    .status
            assertFalse(status.is4xxClientError, "status var $status, forventet ikke 4xx")
        }
    }

    @Nested
    inner class AzureAdAvvistForSøknader {
        @Test
        fun `skal avvise Azure AD-token på barnetrygd-søknad`() {
            val token = JwtTokenTestUtil.lagAzureAdToken(mockOAuth2Server)

            restTestClient
                .post()
                .uri("/api/soknad/v10")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }

        @Test
        fun `skal avvise Azure AD-token på kontantstøtte-søknad v4`() {
            val token = JwtTokenTestUtil.lagAzureAdToken(mockOAuth2Server)

            restTestClient
                .post()
                .uri("/api/kontantstotte/soknad/v4")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }

        @Test
        fun `skal avvise Azure AD-token på kontantstøtte-søknad v5`() {
            val token = JwtTokenTestUtil.lagAzureAdToken(mockOAuth2Server)

            restTestClient
                .post()
                .uri("/api/kontantstotte/soknad/v5")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }

        @Test
        fun `skal avvise Azure AD-token på kontantstøtte-søknad v6`() {
            val token = JwtTokenTestUtil.lagAzureAdToken(mockOAuth2Server)

            restTestClient
                .post()
                .uri("/api/kontantstotte/soknad/v6")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }
    }

    @Nested
    inner class UgyldigeTokens {
        @Test
        fun `skal avvise TokenX-token med feil audience`() {
            val token =
                JwtTokenTestUtil.lagUgyldigToken(
                    mockOAuth2Server,
                    JwtTokenTestUtil.UgyldigTokenÅrsak.FEIL_AUDIENCE,
                )

            restTestClient
                .post()
                .uri("/api/soknad/v10")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }

        @Test
        fun `skal avvise TokenX-token med feil acr`() {
            val token =
                JwtTokenTestUtil.lagUgyldigToken(
                    mockOAuth2Server,
                    JwtTokenTestUtil.UgyldigTokenÅrsak.FEIL_ACR,
                )

            restTestClient
                .post()
                .uri("/api/soknad/v10")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }

        @Test
        fun `skal avvise utgått token`() {
            val token =
                JwtTokenTestUtil.lagUgyldigToken(
                    mockOAuth2Server,
                    JwtTokenTestUtil.UgyldigTokenÅrsak.UTGÅTT,
                )

            restTestClient
                .post()
                .uri("/api/soknad/v10")
                .header("Authorization", "Bearer $token")
                .header(
                    "Content-Type",
                    "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW",
                ).exchange()
                .expectStatus()
                .isUnauthorized
        }

        @Test
        fun `skal avvise token fra ukjent issuer`() {
            val token =
                JwtTokenTestUtil.lagUgyldigToken(
                    mockOAuth2Server,
                    JwtTokenTestUtil.UgyldigTokenÅrsak.FEIL_ISSUER,
                )

            restTestClient
                .post()
                .uri("/api/soknad/v10")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }

        @Test
        fun `skal avvise Azure AD-token med feil audience`() {
            val token =
                JwtTokenTestUtil.lagAzureAdToken(
                    mockOAuth2Server,
                    audience = "feil-audience",
                )

            restTestClient
                .post()
                .uri("/api/soknad/v10")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }
    }
}
