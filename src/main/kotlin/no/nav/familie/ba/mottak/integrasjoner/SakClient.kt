package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.domene.NyBehandling
import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestOperations
import java.net.URI

private val logger = LoggerFactory.getLogger(SakClient::class.java)

@Component
class SakClient @Autowired constructor(@param:Value("\${FAMILIE_BA_SAK_API_URL}") private val sakServiceUri: String,
                                       @Qualifier("clientCredentials") restOperations: RestOperations)
                                        : AbstractRestClient(restOperations, "integrasjon") {


    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun sendTilSak(nyBehandling: NyBehandling) {
        val uri = URI.create("$sakServiceUri/behandlinger")
        logger.info("Sender søknad til {}", uri)
        try {
            val response = putForEntity<Ressurs<String>>(uri, nyBehandling)
            logger.info("Søknad sendt til sak. Status=${response?.status}")
        } catch (e: RestClientResponseException) {
            logger.warn("Innsending til sak feilet. Responskode: {}, body: {}", e.rawStatusCode, e.responseBodyAsString)
            throw IllegalStateException("Innsending til sak feilet. Status: " + e.rawStatusCode
                                        + ", body: " + e.responseBodyAsString, e)
        } catch (e: RestClientException) {
            throw IllegalStateException("Innsending til sak feilet.", e)
        }
    }

    fun hentSaksnummer(personIdent: String): String {
        val uri = URI.create("$sakServiceUri/fagsaker")
        return runCatching {
            postForEntity<Ressurs<RestFagsak>>(uri, mapOf("personIdent" to personIdent))
        }.fold(
            onSuccess = { it.data?.id?.toString() ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
            onFailure = { throw IntegrasjonException("Feil ved henting av saksnummer fra ba-sak.", it, uri, personIdent) }
        )
    }

    fun hentPågåendeSakStatus(personIdent: String): RestPågåendeSakSøk {
        val uri = URI.create("$sakServiceUri/fagsaker/sok/ba-sak-og-infotrygd")
        return runCatching {
            postForEntity<Ressurs<RestPågåendeSakSøk>>(uri, mapOf("personIdent" to personIdent))
        }.fold(
                onSuccess = { it.data?.let(this::valider) ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av sak opplysninger fra ba-sak.", it, uri, personIdent) }
        )
    }

    private fun valider(resultat: RestPågåendeSakSøk) =
            resultat.takeUnless { bruker -> bruker.harPågåendeSakIBaSak && bruker.harPågåendeSakIInfotrygd }
            ?: throw  error("Bruker har pågående sak i både ny og gammel løsning!")

}

data class RestFagsak(val id: Long)

data class RestPågåendeSakSøk(
        val harPågåendeSakIBaSak: Boolean,
        val harPågåendeSakIInfotrygd: Boolean
)
