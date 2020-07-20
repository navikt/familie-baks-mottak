package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.config.PdfgeneratorConfig
import no.nav.familie.ba.mottak.domene.Fil
import no.nav.familie.http.client.AbstractRestClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import org.springframework.web.util.DefaultUriBuilderFactory

@Service
class PdfClient(@Qualifier("restTemplateUnsecured") operations: RestOperations,
                private val pdfgeneratorConfig: PdfgeneratorConfig) : AbstractRestClient(operations, "pdf") {
    fun lagPdf(labelValueJson: Map<String, Any>): Fil {
        val sendInnUri =
                DefaultUriBuilderFactory().uriString(pdfgeneratorConfig.url).path("/template/soknad/download-pdf").build()
        val byteArray = postForEntity<ByteArray>(sendInnUri, labelValueJson)
        return Fil(byteArray)
    }
}