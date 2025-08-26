package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.familie.baks.mottak.AbstractWiremockTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@ActiveProfiles("dev", "mock-oauth")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JournalpostClientTest : AbstractWiremockTest() {
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
                        .withBody(gyldigResponse()),
                ),
        )

        val opprettOppgaveResponse = journalpostClient.hentJournalpost("123")
    }

    @Throws(IOException::class)
    private fun gyldigResponse(): String =
        Files.readString(
            ClassPathResource("testdata/hentJournalpost-response.json").file.toPath(),
            StandardCharsets.UTF_8,
        )
}
