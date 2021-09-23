package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsvedlegg
import no.nav.familie.kontrakter.ba.søknad.v3.Søknadsvedlegg as SøknadsvedleggV3
import no.nav.familie.kontrakter.felles.Ressurs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.lang.RuntimeException
import java.net.URI

private val logger = LoggerFactory.getLogger(FamilieDokumentClient::class.java)

@Component
class FamilieDokumentClient(
    @param:Value("\${FAMILIE_DOKUMENT_API_URL}") private val dokumentUri: URI,
    @Qualifier("clientCredentials") restOperations: RestOperations
) : AbstractRestClient(restOperations, "integrasjon") {

    @Retryable(
        value = [RuntimeException::class],
        backoff = Backoff(delayExpression = "\${retry.backoff.delay:5000}")
    )
    private fun hentVedlegg(dokumentId: String): ByteArray {
        val uri = URI.create("$dokumentUri/api/mapper/ANYTHING/${dokumentId}")

        val response = getForEntity<Ressurs<ByteArray>>(uri)
        return response.data!!
    }

    fun hentVedlegg(vedlegg: Søknadsvedlegg): ByteArray {
        logger.debug("Henter ${vedlegg.navn} for dokumentasjonsbehov ${vedlegg.tittel}")
        return hentVedlegg(vedlegg.dokumentId)
    }

    fun hentVedlegg(vedlegg: SøknadsvedleggV3): ByteArray {
        logger.debug("Henter ${vedlegg.navn} for dokumentasjonsbehov ${vedlegg.tittel}")
        return hentVedlegg(vedlegg.dokumentId)
    }
}