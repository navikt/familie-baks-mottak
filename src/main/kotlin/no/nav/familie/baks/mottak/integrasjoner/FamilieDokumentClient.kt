package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.Ressurs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.net.URI

private val logger = LoggerFactory.getLogger(FamilieDokumentClient::class.java)

@Component
class FamilieDokumentClient(
    @param:Value("\${FAMILIE_DOKUMENT_API_URL}") private val dokumentUri: URI,
    @Qualifier("unauthenticatedRestClient") private val unauthenticatedRestClient: RestClient,
    @Qualifier("familieTokenXRestClient") private val restClient: RestClient,
) {
    fun hentVedlegg(dokumentId: String): ByteArray {
        logger.info("Henter vedlegg med dokumentid $dokumentId")

        val uri = URI.create("$dokumentUri/api/mapper/ANYTHING/$dokumentId")

        val response =
            restClient
                .get()
                .uri(uri)
                .retrieve()
                .body<Ressurs<ByteArray>>()!!
        return response.data!!
    }

    fun lagPdf(html: String): ByteArray {
        val uri = URI.create("$dokumentUri/api/html-til-pdf")
        return unauthenticatedRestClient
            .post()
            .uri(uri)
            .contentType(MediaType.TEXT_HTML)
            .accept(MediaType.APPLICATION_PDF)
            .body(html.encodeToByteArray())
            .retrieve()
            .body(ByteArray::class.java)!!
    }
}
