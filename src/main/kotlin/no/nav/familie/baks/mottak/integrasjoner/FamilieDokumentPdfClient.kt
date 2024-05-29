package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.dokgen.DokGen

class FamilieDokumentPdfClient(
    private val familieDokumentClient: FamilieDokumentClient,
    private val dokGen: DokGen,
) : PdfClient {
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
