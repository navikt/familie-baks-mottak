package no.nav.familie.baks.mottak.søknad

import io.restassured.RestAssured
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import no.nav.familie.baks.mottak.DevLauncher
import no.nav.familie.kontrakter.felles.Tema
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [DevLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableMockOAuth2Server
@Tag("integration")
@ActiveProfiles("dev")
class SøknadControllerTest {
    private val controllerUrl: String = "/api/soknad"

    @LocalServerPort
    var port: Int = 0

    @BeforeEach
    fun setUp() {
        RestAssured.port = port
    }

    @Test
    fun `skal returnere UNAUTHORIZED om token ikke er satt`() {
        // Act & assert
        When {
            get("$controllerUrl/hent-personer-i-digital-soknad/${Tema.BAR}/1")
        } Then {
            statusCode(HttpStatus.UNAUTHORIZED.value())
        }
    }
}