package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.domene.Fil
import no.nav.familie.http.client.AbstractRestClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import java.net.URI

@Service
class PdfClient(@Qualifier("restTemplateUnsecured") operations: RestOperations,
                @Value("\${FAMILIE_BA_DOKGEN_API_URL}") private val dokgenUri: String) : AbstractRestClient(operations, "pdf") {
    fun lagPdf(labelValueJson: Map<String, Any>): Fil {
        val sendInnUri = URI.create("$dokgenUri/template/soknad/download-pdf")
        val byteArray = postForEntity<ByteArray>(sendInnUri, labelValueJson)
        return Fil(byteArray)
    }
}
