package no.nav.familie.baks.mottak.søknad

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.familie.baks.mottak.integrasjoner.PdfClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.tilDBKontantstøtteSøknad
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV10
import no.nav.familie.kontrakter.ba.søknad.v10.BarnetrygdSøknad
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV6
import no.nav.familie.kontrakter.ks.søknad.v6.KontantstøtteSøknad
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class PdfServiceTest {
    private val mockFamilieDokumentPdfClient: PdfClient = mockk()
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService = SøknadSpråkvelgerService()

    private val pdfService = PdfService(familieDokumentPdfClient = mockFamilieDokumentPdfClient, søknadSpråkvelgerService = søknadSpråkvelgerService)

    @Test
    fun `mapper fra søknad kontrakt til dokgen barnetrygd input`() {
        val jsonSlot = slot<Map<String, Any>>()
        every { mockFamilieDokumentPdfClient.lagPdf(any(), capture(jsonSlot)) } returns ByteArray(0)

        val jsonString: String =
            File("./src/test/kotlin/no/nav/familie/baks/mottak/søknad/testdata/barnetrygd-søknad.json")
                .readText(Charsets.UTF_8)
        val barnetrygdSøknad: BarnetrygdSøknad = jsonMapper.readValue(jsonString, BarnetrygdSøknad::class.java)
        val dbBarnetrygdSøknad: DBBarnetrygdSøknad = barnetrygdSøknad.tilDBSøknad()
        pdfService.lagBarnetrygdPdf(VersjonertBarnetrygdSøknadV10(barnetrygdSøknad = barnetrygdSøknad), dbBarnetrygdSøknad, språk = "nb")

        // Kommenter inn dette for å logge generert json til console
        // val jsonToDokgen: String = mapper.writeValueAsString(jsonSlot)
        // println(jsonToDokgen)

        assertThat(jsonSlot.captured["kontraktVersjon"]).isEqualTo(10)
    }

    @Test
    fun `mapper fra søknad kontrakt til dokgen kontantstøtte input`() {
        val jsonSlot = slot<Map<String, Any>>()
        every { mockFamilieDokumentPdfClient.lagPdf(any(), capture(jsonSlot)) } returns ByteArray(0)

        val jsonString: String =
            File("./src/test/kotlin/no/nav/familie/baks/mottak/søknad/testdata/kontantstøtte-søknad.json")
                .readText(Charsets.UTF_8)
        val kontantstøtteSøknad: KontantstøtteSøknad = jsonMapper.readValue(jsonString, KontantstøtteSøknad::class.java)
        val dbKontantstøtteSøknad: DBKontantstøtteSøknad = kontantstøtteSøknad.tilDBKontantstøtteSøknad()
        pdfService.lagKontantstøttePdf(VersjonertKontantstøtteSøknadV6(kontantstøtteSøknad = kontantstøtteSøknad), dbKontantstøtteSøknad, språk = "nb")

        // Kommenter inn dette for å logge generert json til console
        // val jsonToDokgen: String = mapper.writeValueAsString(jsonSlot)
        // println(jsonToDokgen)

        assertThat(jsonSlot.captured["kontraktVersjon"]).isEqualTo(6)
    }
}
