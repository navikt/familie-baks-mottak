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
import java.time.LocalDateTime

private val logger = LoggerFactory.getLogger(SakClient::class.java)

@Component
class SakClient @Autowired constructor(@param:Value("\${FAMILIE_BA_SAK_API_URL}") private val sakServiceUri: String,
                                       @Qualifier("clientCredentials") restOperations: RestOperations)
                                        : AbstractRestClient(restOperations, "integrasjon") {


    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delayExpression = "\${retry.backoff.delay:5000}"))
    fun sendTilSak(nyBehandling: NyBehandling) {
        val uri = URI.create("$sakServiceUri/behandlinger")
        logger.info("Sender søknad til {}", uri)
        try {
            val response = putForEntity<Ressurs<String>>(uri, nyBehandling)
            logger.info("Søknad sendt til sak. Status=${response.status}")
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

    fun hentRestFagsakDeltagerListe(personIdent: String,
                                    barnasIdenter: List<String> = emptyList()): List<RestFagsakDeltager> {
        val uri = URI.create("$sakServiceUri/fagsaker/sok/fagsakdeltagere")
        return runCatching {
            postForEntity<Ressurs<List<RestFagsakDeltager>>>(uri, RestSøkParam(personIdent, barnasIdenter))
        }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av fagsakdeltagere fra ba-sak.", it, uri, personIdent) }
        )
    }

    fun hentRestFagsak(personIdent: String): RestFagsak {
        val uri = URI.create("$sakServiceUri/fagsaker/hent-fagsak-paa-person")
        return runCatching {
            postForEntity<Ressurs<RestFagsak>>(uri, mapOf("personIdent" to personIdent))
        }.fold(
            onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
            onFailure = { throw IntegrasjonException("Feil ved henting av RestFagsak fra ba-sak.", it, uri, personIdent) }
        )
    }

    fun hentRestFagsak(fagsakId: Long): RestFagsak {
        val uri = URI.create("$sakServiceUri/fagsaker/$fagsakId")
        return runCatching {
            getForEntity<Ressurs<RestFagsak>>(uri)
        }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av RestFagsak fra ba-sak.", it, uri) }
        )
    }

}

data class RestFagsak(val id: Long,
                      val behandlinger: List<RestUtvidetBehandling>)

data class RestUtvidetBehandling(val aktiv: Boolean,
                                 val arbeidsfordelingPåBehandling: RestArbeidsfordelingPåBehandling,
                                 val behandlingId: Long,
                                 val kategori: BehandlingKategori,
                                 val opprettetTidspunkt: LocalDateTime,
                                 val resultat: String,
                                 val steg: String,
                                 val type: String,
                                 val underkategori: BehandlingUnderkategori,)

data class RestArbeidsfordelingPåBehandling(
        val behandlendeEnhetId: String,
)

enum class BehandlingKategori {
    EØS,
    NASJONAL
}

enum class BehandlingUnderkategori {
    UTVIDET,
    ORDINÆR
}

data class RestSøkParam(
        val personIdent: String,
        val barnasIdenter: List<String> = emptyList()
)

data class RestFagsakDeltager(
        val ident: String,
        val rolle: FagsakDeltagerRolle,
        val fagsakId: Long,
        val fagsakStatus: FagsakStatus,

)

enum class FagsakDeltagerRolle {
    BARN,
    FORELDER,
    UKJENT
}

enum class FagsakStatus {
    OPPRETTET,
    LØPENDE, // Har minst én behandling gjeldende for fremtidig utbetaling
    AVSLUTTET
}
