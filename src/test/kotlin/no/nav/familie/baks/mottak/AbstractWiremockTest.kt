package no.nav.familie.baks.mottak

import org.springframework.boot.test.context.SpringBootTest
<<<<<<< HEAD
import org.wiremock.spring.EnableWireMock
=======
>>>>>>> fba9b4e (Fikser en del kompileringsfeil)

@SpringBootTest(
    classes = [DevLauncher::class],
    properties = [
        "FAMILIE_INTEGRASJONER_API_URL=http://localhost:\${wiremock.server.port}/api",
        "PDL_URL=http://localhost:\${wiremock.server.port}/api",
        "NORG2_API_URL=http://localhost:\${wiremock.server.port}/norg2",
        "FAMILIE_BA_INFOTRYGD_API_URL=http://localhost:\${wiremock.server.port}",
        "FAMILIE_BA_SAK_API_URL=http://localhost:\${wiremock.server.port}/api",
        "FAMILIE_KS_SAK_API_URL=http://localhost:\${wiremock.server.port}/api",
    ],
)
@EnableWireMock
abstract class AbstractWiremockTest
