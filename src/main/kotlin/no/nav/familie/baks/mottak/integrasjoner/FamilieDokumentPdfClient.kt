package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.dokgen.DokGen
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FamilieDokumentPdfClient(
    private val familieDokumentClient: FamilieDokumentClient,
) : PdfClient {
    private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")
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

        secureLogger.info("html: $html")
        return familieDokumentClient.lagPdf(html)
    }
}
