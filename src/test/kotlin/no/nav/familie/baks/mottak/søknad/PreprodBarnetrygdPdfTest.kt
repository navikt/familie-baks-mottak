package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentPdfClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV10
import no.nav.familie.kontrakter.ba.søknad.v10.BarnetrygdSøknad
import no.nav.familie.kontrakter.felles.jsonMapper
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.URI
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

/**
 * Krever tilgang til preprod. Kjør med
 * mvn -Dkotlin.compiler.daemon=false -Dtest=PreprodBarnetrygdPdfTest -DrunPreprodPdfTest=true test.
 * Fasit i testdata/dokgen.pdf er laget av testdata/dokgen.json med dokumentdato 23.10.2026.
 */
@EnabledIfSystemProperty(named = "runPreprodPdfTest", matches = "true")
class PreprodBarnetrygdPdfTest {
    @Test
    fun `PDF fra versjonert barnetrygdsøknad V10 i preprod stemmer med fasit`() {
        val baseUri = URI.create("https://familie-dokument.intern.dev.nav.no")
        val requestFactory =
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build())
                .apply { setReadTimeout(Duration.ofSeconds(60)) }
        val restClient = RestClient.builder().requestFactory(requestFactory).build()
        val dokumentClient = FamilieDokumentClient(baseUri, restClient, restClient)
        val pdfService = PdfService(FamilieDokumentPdfClient(dokumentClient), SøknadSpråkvelgerService())

        val søknad: BarnetrygdSøknad =
            jsonMapper.readValue(
                requireNotNull(javaClass.getResource("/testdata/preprod-pdf-test.json")) { "Mangler preprod-pdf-test.json" }.readText(),
                BarnetrygdSøknad::class.java,
            )
        val dbSøknad = søknad.tilDBSøknad().copy(opprettetTid = LocalDateTime.of(2026, 10, 23, 12, 0))
        val pdf = pdfService.lagBarnetrygdPdf(VersjonertBarnetrygdSøknadV10(barnetrygdSøknad = søknad), dbSøknad, "nb")
        Files.write(Path.of("target/preprod-barnetrygd-v10-generert.pdf"), pdf)

        assertThat(pdf.size).isGreaterThan(5)
        assertThat(pdf.copyOfRange(0, 5).toString(Charsets.US_ASCII)).isEqualTo("%PDF-")
        Loader.loadPDF(pdf).use { dokument ->
            val fasit = requireNotNull(javaClass.getResourceAsStream("/testdata/preprod-pdf-test.pdf")) { "Mangler preprod-pdf-test.pdf" }.use { it.readBytes() }
            Loader.loadPDF(fasit).use { forventet ->
                assertThat(dokument.numberOfPages).isPositive().isEqualTo(forventet.numberOfPages)
                for (side in 1..dokument.numberOfPages) {
                    val tekst =
                        PDFTextStripper().apply {
                            startPage = side
                            endPage = side
                        }
                    val faktiskTekst = tekst.getText(dokument).replace(Regex("\\s+"), " ").trim()
                    val forventetTekst = tekst.getText(forventet).replace(Regex("\\s+"), " ").trim()
                    assertThat(faktiskTekst == forventetTekst)
                        .describedAs("Tekst på side $side er lik fasit")
                        .isTrue()
                }
            }
        }
    }
}
