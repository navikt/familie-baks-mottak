package no.nav.familie.ba.mottak.søknad

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.familie.ba.mottak.integrasjoner.PdfClient
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.ba.mottak.søknad.domene.SøknadSpråkvelgerService
import no.nav.familie.ba.mottak.søknad.domene.SøknadV8
import no.nav.familie.kontrakter.ba.søknad.v8.Søknad as SøknadKontraktV8
import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class PdfServiceTest {

    private val mockPdfClient: PdfClient = mockk<PdfClient>()
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService = SøknadSpråkvelgerService()

    private val pdfService = PdfService(mockPdfClient, søknadSpråkvelgerService)

    @Test
    fun `mapper fra søknad kontrakt til dokgen input`() {
        val jsonSlot = slot<Map<String, Any>>()
        every { mockPdfClient.lagPdf(capture(jsonSlot), any()) } returns ByteArray(0)

        val mapper = jacksonObjectMapper()
        mapper.registerKotlinModule()
        mapper.registerModule(JavaTimeModule())

        val jsonString: String = File("./src/test/kotlin/no/nav/familie/ba/mottak/søknad/testdata/testdata1.json")
            .readText(Charsets.UTF_8)
        val søknad: SøknadKontraktV8 = mapper.readValue(jsonString)
        val dbSøknad: DBSøknad = søknad.tilDBSøknad()
        pdfService.lagPdf(SøknadV8(søknad = søknad), dbSøknad, språk = "nb")

        // Kommenter inn dette for å logge generert json til console
        // val jsonToDokgen: String = mapper.writeValueAsString(jsonSlot)
        // println(jsonToDokgen)

        assertThat(jsonSlot.captured["kontraktVersjon"]).isEqualTo(8)
    }
}
