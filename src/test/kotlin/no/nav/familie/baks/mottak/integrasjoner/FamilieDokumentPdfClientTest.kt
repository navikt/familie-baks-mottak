package no.nav.familie.baks.mottak.integrasjoner

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.familie.baks.dokgen.DokGen
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class FamilieDokumentPdfClientTest {
    private val familieDokumentClient: FamilieDokumentClient = mockk()

    @Test
    fun lagPdf() {
        // Arrange
        val capturedHtml = slot<String>()

        val mockedByteArray = ByteArray(1)

        every { familieDokumentClient.lagPdf(capture(capturedHtml)) } returns mockedByteArray

        val familieDokumentPdfClient =
            FamilieDokumentPdfClient(
                familieDokumentClient,
                DokGen(),
            )

        val inputData =
            File("./src/test/kotlin/no/nav/familie/baks/mottak/søknad/testdata/dokgen/testdata_input.json").readText()
                .let {
                    ObjectMapper().readValue(it, Map::class.java) as Map<String, Any>
                }

        val forventetResultat =
            File("./src/test/kotlin/no/nav/familie/baks/mottak/søknad/testdata/dokgen/eksempel_output.html").readText()

        // Act
        val pdfByteArray: ByteArray =
            familieDokumentPdfClient.lagPdf(
                "soknad",
                inputData,
            )

        // Assert
        assertThat(pdfByteArray).isEqualTo(mockedByteArray)
        assertThat(capturedHtml.captured.lines().joinToString(transform = String::trimEnd))
            .isEqualTo(forventetResultat.lines().joinToString(transform = String::trimEnd))
    }
}
