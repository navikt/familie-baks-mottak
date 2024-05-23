package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.net.URI

private val logger = LoggerFactory.getLogger(FamilieDokumentClient::class.java)

@Component
class FamilieDokumentClient(
    @param:Value("\${FAMILIE_DOKUMENT_API_URL}") private val dokumentUri: URI,
    @Qualifier("clientCredentials") restOperations: RestOperations,
) : AbstractRestClient(restOperations, "integrasjon") {
    @Retryable(
        value = [RuntimeException::class],
        backoff = Backoff(delayExpression = "\${retry.backoff.delay:5000}"),
    )
    fun hentVedlegg(dokumentId: String): ByteArray {
        val uri = URI.create("$dokumentUri/api/mapper/ANYTHING/$dokumentId")
        val response = getForEntity<Ressurs<ByteArray>>(uri)
        return response.data!!
    }

    fun lagPdf(html: String): ByteArray {
        val sendInnUri = URI.create("$dokumentUri/api/html-til-pdf")
        return postForEntity(
            uri = sendInnUri,
            payload = html.encodeToByteArray(),
            httpHeaders = HttpHeaders().apply {
                contentType = MediaType.TEXT_HTML
                accept = listOf(MediaType.APPLICATION_PDF)
            },
        )
    }
}
