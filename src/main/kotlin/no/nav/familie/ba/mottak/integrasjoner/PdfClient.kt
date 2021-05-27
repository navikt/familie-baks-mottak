package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.objectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import java.lang.RuntimeException
import java.net.URI

@Service
class PdfClient(@Qualifier("restTemplateUnsecured") operations: RestOperations,
                @Value("\${FAMILIE_BA_DOKGEN_API_URL}") private val dokgenUri: String) : AbstractRestClient(operations, "pdf") {
    fun lagPdf(labelValueJson: Map<String, Any>): ByteArray {
        val sendInnUri = URI.create("$dokgenUri/template/soknad/download-pdf")
        try {
            return postForEntity(sendInnUri, labelValueJson)
        } catch (e: RuntimeException) {
            val asString = objectMapper.writeValueAsString(labelValueJson)
            logger.info(asString)
            throw e
        }
    }

    companion object {
        val logger = LoggerFactory.getLogger(this::class.java)
    }
}
