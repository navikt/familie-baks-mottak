package no.nav.familie.baks.mottak.integrasjoner

interface PdfClient {
    fun lagPdf(
        templateNavn: String,
        inputData: Map<String, Any>,
    ): ByteArray
}
