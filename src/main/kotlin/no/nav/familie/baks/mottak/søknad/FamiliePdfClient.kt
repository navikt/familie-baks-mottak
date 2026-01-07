package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.domene.FeltMap
import no.nav.familie.restklient.client.AbstractPingableRestClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

data class PdfResponse(
    val pdf: ByteArray,
)

@Service
class FamiliePdfClient(
    @Value("\${FAMILIE_PDF_URL}")
    private val uri: URI,
    @Qualifier("clientCredentials")
    restOperations: RestOperations,
) : AbstractPingableRestClient(restOperations, "familie-pdf") {
    override val pingUri: URI =
        UriComponentsBuilder
            .fromUri(uri)
            .pathSegment("api/ping")
            .build()
            .toUri()

    fun opprettPdf(feltMap: FeltMap): ByteArray {
        val uri =
            UriComponentsBuilder
                .fromUri(uri)
                .pathSegment("api/v1/pdf/opprett-pdf/som-json")
                .build()
                .toUri()

        val response: PdfResponse = postForEntity(uri, feltMap, HttpHeaders().medContentTypeJsonUTF8())

        return response.pdf
    }
}

private fun HttpHeaders.medContentTypeJsonUTF8(): HttpHeaders {
    this.add("Content-Type", "application/json;charset=UTF-8")
    this.acceptCharset = listOf(Charsets.UTF_8)
    return this
}
