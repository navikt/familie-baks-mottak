package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.domene.FeltMap
import no.nav.familie.felles.texas.TexasRestClientFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

data class PdfResponse(
    val pdf: ByteArray,
)

@Service
class FamiliePdfClient(
    @Value("\${FAMILIE_PDF_URL}") private val uri: URI,
    @Value("\${FAMILIE_PDF_SCOPE}") private val familiePdfScope: String,
    texasRestClientFactory: TexasRestClientFactory,
) {
    private val restClient = texasRestClientFactory.lagMaskinTilMaskinRestKlient(familiePdfScope)

    fun opprettPdf(feltMap: FeltMap): ByteArray {
        val pdfUri =
            UriComponentsBuilder
                .fromUri(uri)
                .pathSegment("api/v1/pdf/opprett-pdf/som-json")
                .build()
                .toUri()

        val response: PdfResponse =
            restClient
                .post()
                .uri(pdfUri)
                .contentType(MediaType.APPLICATION_JSON)
                .acceptCharset(Charsets.UTF_8)
                .body(feltMap)
                .retrieve()
                .body(PdfResponse::class.java)!!

        return response.pdf
    }
}
