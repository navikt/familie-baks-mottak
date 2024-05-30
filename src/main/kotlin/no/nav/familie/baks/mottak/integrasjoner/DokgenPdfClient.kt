package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import java.net.URI

@Deprecated(
    message = "Skal fjernes når FamilieDokumentPdfClient taes i bruk uten feature toggle",
)
@Service
class DokgenPdfClient(
    @Qualifier("restTemplateUnsecured") operations: RestOperations,
    @Value("\${FAMILIE_BAKS_DOKGEN_API_URL}") private val dokgenUri: String,
) : PdfClient, AbstractRestClient(operations, "pdf") {
    override fun lagPdf(
        templateNavn: String,
        inputData: Map<String, Any>,
    ): ByteArray {
        val sendInnUri = URI.create("$dokgenUri/template/$templateNavn/download-pdf")
        return postForEntity(sendInnUri, inputData)
    }
}
