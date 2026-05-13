package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.felles.tokenklient.tokenx.TokenXClient
import no.nav.familie.kontrakter.felles.Ressurs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.net.URI

private val logger = LoggerFactory.getLogger(FamilieDokumentClient::class.java)

@Component
class FamilieDokumentClient(
    @param:Value("\${FAMILIE_DOKUMENT_API_URL}") private val dokumentUri: URI,
    @param:Value("\${FAMILIE_DOKUMENT_SCOPE}") private val familieDokumentScope: String,
    private val tokenxClient: TokenXClient,
    @Qualifier("unauthenticatedRestClient") private val unauthenticatedRestClient: RestClient,
) {
    fun hentVedlegg(dokumentId: String): ByteArray {
        logger.info("Henter vedlegg med dokumentid $dokumentId")

        val nåværendeContext = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken

        val nyttToken =
            tokenxClient
                .hentToken(familieDokumentScope, nåværendeContext.token.tokenValue)

        val restClient = RestClient.builder().defaultHeader("Authorization", "Bearer $nyttToken").build()

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
