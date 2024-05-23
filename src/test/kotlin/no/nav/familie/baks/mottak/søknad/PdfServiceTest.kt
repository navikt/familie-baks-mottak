package no.nav.familie.baks.mottak.søknad

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import no.nav.familie.kontrakter.ba.søknad.v8.Søknad as SøknadKontraktV8
import no.nav.familie.baks.dokgen.DokGen
import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class PdfServiceTest {
    private val mockFamilieDokumentClient: FamilieDokumentClient = mockk(relaxed = true)
    private val mockDokgen: DokGen = mockk()
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService = SøknadSpråkvelgerService()

    private val pdfService = PdfService(mockFamilieDokumentClient, søknadSpråkvelgerService, mockDokgen)

    @Test
    fun `mapper fra søknad kontrakt til dokgen input`() {
        val input = slot<Map<String, Any>>()
        every { mockDokgen.lagHtmlTilPdf(any(), capture(input)) } returns "<html/>"

        val mapper = jacksonObjectMapper()
        mapper.registerKotlinModule()
        mapper.registerModule(JavaTimeModule())

        val jsonString: String =
            File("./src/test/kotlin/no/nav/familie/baks/mottak/søknad/testdata/testdata1.json")
                .readText(Charsets.UTF_8)
        val søknad: SøknadKontraktV8 = mapper.readValue(jsonString)
        val dbSøknad: DBSøknad = søknad.tilDBSøknad()
        pdfService.lagBarnetrygdPdf(SøknadV8(søknad = søknad), dbSøknad, språk = "nb")

        // Kommenter inn dette for å logge generert input til console
        // println(input.captured)

        assertThat(input.captured["kontraktVersjon"]).isEqualTo(8)
    }
}
