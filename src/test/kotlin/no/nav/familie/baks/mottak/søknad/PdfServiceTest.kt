package no.nav.familie.baks.mottak.søknad

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.familie.baks.mottak.integrasjoner.PdfClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.unleash.UnleashService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import no.nav.familie.kontrakter.ba.søknad.v8.Søknad as SøknadKontraktV8

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class PdfServiceTest {
    private val mockFamilieDokumentPdfClient: PdfClient = mockk()
    private val mockDokgenPdfClient: PdfClient = mockk()
    private val mockUnleashService: UnleashService = mockk(relaxed = true)
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService = SøknadSpråkvelgerService()

    private val pdfService = PdfService(familieDokumentPdfClient = mockFamilieDokumentPdfClient, dokgenPdfClient = mockDokgenPdfClient, søknadSpråkvelgerService = søknadSpråkvelgerService, unleashService = mockUnleashService)

    @Test
    fun `mapper fra søknad kontrakt til dokgen input`() {
        val jsonSlot = slot<Map<String, Any>>()
        every { mockFamilieDokumentPdfClient.lagPdf(any(), capture(jsonSlot)) } returns ByteArray(0)
        every { mockDokgenPdfClient.lagPdf(any(), capture(jsonSlot)) } returns ByteArray(0)

        val mapper = jacksonObjectMapper()
        mapper.registerKotlinModule()
        mapper.registerModule(JavaTimeModule())

        val jsonString: String =
            File("./src/test/kotlin/no/nav/familie/baks/mottak/søknad/testdata/testdata1.json")
                .readText(Charsets.UTF_8)
        val søknad: SøknadKontraktV8 = mapper.readValue(jsonString)
        val dbBarnetrygdSøknad: DBBarnetrygdSøknad = søknad.tilDBSøknad()
        pdfService.lagBarnetrygdPdf(SøknadV8(søknad = søknad), dbBarnetrygdSøknad, språk = "nb")

        // Kommenter inn dette for å logge generert json til console
        // val jsonToDokgen: String = mapper.writeValueAsString(jsonSlot)
        // println(jsonToDokgen)

        assertThat(jsonSlot.captured["kontraktVersjon"]).isEqualTo(8)
    }
}
