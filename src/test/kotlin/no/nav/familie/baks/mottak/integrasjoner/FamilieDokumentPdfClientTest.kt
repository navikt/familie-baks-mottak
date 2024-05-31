package no.nav.familie.baks.mottak.integrasjoner

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class FamilieDokumentPdfClientTest {
    private val familieDokumentClient: FamilieDokumentClient = mockk()

    @Test
    fun lagPdf() {
        // Arrange
        val outputHtml = slot<String>()

        val mockedByteArray = ByteArray(1)

        every { familieDokumentClient.lagPdf(capture(outputHtml)) } returns mockedByteArray

        val familieDokumentPdfClient =
            FamilieDokumentPdfClient(
                familieDokumentClient,
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
        assertThat(outputHtml.captured.lines().joinToString(transform = String::trimEnd))
            .isEqualTo(forventetResultat.lines().joinToString(transform = String::trimEnd))
    }
}
