package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.ba.søknad.Søknadsvedlegg
import no.nav.familie.kontrakter.felles.Ressurs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.net.URI

private val logger = LoggerFactory.getLogger(FamilieDokumentClient::class.java)

@Component
class FamilieDokumentClient(
    @param:Value("\${FAMILIE_DOKUMENT_API_URL}") private val dokumentUri: URI,
    @Qualifier("clientCredentials") restOperations: RestOperations
) : AbstractRestClient(restOperations, "integrasjon") {

    fun hentVedlegg(vedlegg: Søknadsvedlegg): ByteArray {
        logger.debug("Henter ${vedlegg.navn} for dokumentasjonsbehov ${vedlegg.tittel}")
        val uri = URI.create("$dokumentUri/api/mapper/ANYTHING/${vedlegg.dokumentId}")

        val response = getForEntity<Ressurs<ByteArray>>(uri)
        return response.data!!
    }
}