package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.PersonIdent
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

private val logger = LoggerFactory.getLogger(KsSakClient::class.java)

@Component
class KsSakClient
    @Autowired
    constructor(
        @param:Value("\${FAMILIE_KS_SAK_API_URL}") private val ksSakServiceUri: String,
        @Qualifier("clientCredentials") restOperations: RestOperations,
    ) : AbstractRestClient(restOperations, "integrasjon") {
        fun hentSaksnummer(personIdent: String): String {
            val uri = URI.create("$ksSakServiceUri/fagsaker")
            return runCatching {
                postForEntity<Ressurs<RestFagsak>>(uri, mapOf("personIdent" to personIdent))
            }.fold(
                onSuccess = { it.data?.id?.toString() ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av saksnummer fra ks-sak.", it, uri, personIdent) },
            )
        }

        fun hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(
            personIdent: String,
        ): List<RestFagsakIdOgTilknyttetAktørId> {
            val uri = URI.create("$ksSakServiceUri/fagsaker/sok/fagsaker-hvor-person-er-deltaker")
            return runCatching {
                postForEntity<Ressurs<List<RestFagsakIdOgTilknyttetAktørId>>>(uri, RestPersonIdent(personIdent))
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av fagsakId og aktørId fra ks-sak.", it, uri, personIdent) },
            )
        }

        fun hentMinimalRestFagsak(fagsakId: Long): RestMinimalFagsak {
            val uri = URI.create("$ksSakServiceUri/fagsaker/minimal/$fagsakId")
            return runCatching {
                getForEntity<Ressurs<RestMinimalFagsak>>(uri)
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av RestFagsak fra ks-sak.", it, uri) },
            )
        }

    @Retryable(
        value = [RuntimeException::class],
        maxAttempts = 3,
        backoff = Backoff(delayExpression = "60000"),
    )
    fun sendIdenthendelseTilKsSak(personIdent: PersonIdent) {
        val uri = URI.create("$ksSakServiceUri/ident")
        try {
            val response = postForEntity<Ressurs<String>>(uri, personIdent)
            secureLogger.info("Identhendelse sendt til ks-sak for $personIdent. Status=${response.status}")
        } catch (e: RestClientResponseException) {
            logger.info("Innsending av identhendelse til ks-sak feilet. Responskode: {}, body: {}", e.rawStatusCode, e.responseBodyAsString)
            secureLogger.info("Innsending av identhendelse til ks-sak feilet for $personIdent. Responskode: {}, body: {}", e.rawStatusCode, e.responseBodyAsString)
            throw IllegalStateException(
                "Innsending av identhendelse til ks-sak feilet. Status: " + e.rawStatusCode +
                        ", body: " + e.responseBodyAsString,
                e,
            )
        } catch (e: RestClientException) {
            secureLogger.warn("Innsending av identhendelse til ks-sak feilet for $personIdent", e)
            throw IllegalStateException("Innsending av identhendelse til ks-sak feilet.", e)
        }
    }
    }
