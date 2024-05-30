package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.dokgen.DokGen
import org.springframework.stereotype.Service

@Service
class FamilieDokumentPdfClient(
    private val familieDokumentClient: FamilieDokumentClient,
) : PdfClient {
    private val dokGen = DokGen()

    override fun lagPdf(
        templateNavn: String,
        inputData: Map<String, Any>,
    ): ByteArray {
        val html =
            dokGen.lagHtmlTilPdf(
                templateNavn,
                inputData,
            )
        return familieDokumentClient.lagPdf(html)
    }
}
