package no.nav.familie.baks.mottak.søknad

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentPdfClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.tilDBKontantstøtteSøknad
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV10
import no.nav.familie.kontrakter.ba.søknad.v10.BarnetrygdSøknad
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV6
import no.nav.familie.kontrakter.ks.søknad.v6.KontantstøtteSøknad
import org.assertj.core.api.Assertions.assertThat
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.io.File

class DokgenSøknadTest {
    private val dokumentClient: FamilieDokumentClient = mockk()
    private val html = slot<String>()
    private val pdf = "pdf".toByteArray()
    private val pdfService = PdfService(FamilieDokumentPdfClient(dokumentClient), SøknadSpråkvelgerService())

    @Test
    fun `ordinær barnetrygd fra søknadskontrakt gir HTML med søker og barn`() {
        every { dokumentClient.lagPdf(capture(html)) } returns pdf
        val søknad = lesBarnetrygdSøknad()

        val resultat = pdfService.lagBarnetrygdPdf(VersjonertBarnetrygdSøknadV10(barnetrygdSøknad = søknad), søknad.tilDBSøknad(), "nb")

        val dokument = Jsoup.parse(html.captured)
        assertThat(resultat).isEqualTo(pdf)
        assertThat(dokument.selectFirst("#overskrift")?.text()).isEqualTo("Søknad om ordinær barnetrygd")
        assertThat(dokument.select("#content").text()).contains("Om barna dine", "Barn Barnessen III")
    }

    @Test
    fun `utvidet barnetrygd fra søknadskontrakt bruker utvidet mal`() {
        every { dokumentClient.lagPdf(capture(html)) } returns pdf
        val søknad = lesBarnetrygdSøknad().copy(søknadstype = Søknadstype.UTVIDET)

        val resultat = pdfService.lagBarnetrygdPdf(VersjonertBarnetrygdSøknadV10(barnetrygdSøknad = søknad), søknad.tilDBSøknad(), "nb")

        val dokument = Jsoup.parse(html.captured)
        assertThat(resultat).isEqualTo(pdf)
        assertThat(dokument.selectFirst("#overskrift")?.text()).isEqualTo("Søknad om utvidet barnetrygd")
        assertThat(dokument.select("#content").text()).contains("Om barna dine", "Barn Barnessen III")
    }

    @Test
    fun `barnetrygd på engelsk gir oversatt innhold i HTML`() {
        every { dokumentClient.lagPdf(capture(html)) } returns pdf
        val søknad = lesBarnetrygdSøknad()

        val resultat = pdfService.lagBarnetrygdPdf(VersjonertBarnetrygdSøknadV10(barnetrygdSøknad = søknad), søknad.tilDBSøknad(), "en")

        val dokument = Jsoup.parse(html.captured)
        assertThat(resultat).isEqualTo(pdf)
        assertThat(dokument.select("#content").text()).contains("About your children", "Barn Barnessen III")
    }

    @Test
    fun `kontantstøtte fra søknadskontrakt gir HTML med søker og barn`() {
        every { dokumentClient.lagPdf(capture(html)) } returns pdf
        val søknad: KontantstøtteSøknad =
            jsonMapper.readValue(lesTestdata("kontantstøtte-søknad.json"), KontantstøtteSøknad::class.java)

        val resultat = pdfService.lagKontantstøttePdf(VersjonertKontantstøtteSøknadV6(kontantstøtteSøknad = søknad), søknad.tilDBKontantstøtteSøknad(), "nb")

        val dokument = Jsoup.parse(html.captured)
        assertThat(resultat).isEqualTo(pdf)
        assertThat(dokument.selectFirst("#overskrift")?.text()).isEqualTo("Søknad om kontantstøtte")
        assertThat(dokument.select("#content").text()).contains("Om deg", "Hvilke barn søker du for?", "TROVERDIG DETALJ")
    }

    private fun lesBarnetrygdSøknad(): BarnetrygdSøknad = jsonMapper.readValue(lesTestdata("barnetrygd-søknad.json"), BarnetrygdSøknad::class.java)

    private fun lesTestdata(filnavn: String): String = File("src/test/kotlin/no/nav/familie/baks/mottak/søknad/testdata/$filnavn").readText(Charsets.UTF_8)
}
