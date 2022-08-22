package no.nav.familie.ba.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.familie.ba.mottak.DevLauncher
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@SpringBootTest(classes = [DevLauncher::class], properties = ["FAMILIE_INTEGRASJONER_API_URL=http://localhost:28085/api"])
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JournalpostClientTest {

    @Autowired
    lateinit var journalpostClient: JournalpostClient

    @Test
    @Tag("integration")
    fun `hentJournalpost skal returnere journalpost`() {
        stubFor(
            get(urlEqualTo("/api/journalpost?journalpostId=123"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(gyldigResponse())
                )
        )

        val opprettOppgaveResponse = journalpostClient.hentJournalpost("123")
    }

    @Throws(IOException::class)
    private fun gyldigResponse(): String {
        return Files.readString(
            ClassPathResource("testdata/hentJournalpost-response.json").file.toPath(),
            StandardCharsets.UTF_8
        )
    }
}
