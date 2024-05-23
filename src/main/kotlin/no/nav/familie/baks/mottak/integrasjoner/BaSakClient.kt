package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.domene.NyBehandling
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
import java.time.LocalDateTime

private val logger = LoggerFactory.getLogger(BaSakClient::class.java)

@Component
class BaSakClient
    @Autowired
    constructor(
        @param:Value("\${FAMILIE_BA_SAK_API_URL}") private val sakServiceUri: String,
        @Qualifier("clientCredentials") restOperations: RestOperations,
    ) : AbstractRestClient(restOperations, "integrasjon") {
        @Retryable(
            value = [RuntimeException::class],
            maxAttempts = 3,
            backoff = Backoff(delayExpression = "\${retry.backoff.delay:5000}"),
        )
        fun sendTilSak(nyBehandling: NyBehandling) {
            val uri = URI.create("$sakServiceUri/behandlinger")
            logger.info("Sender søknad til {}", uri)
            try {
                val response = putForEntity<Ressurs<String>>(uri, nyBehandling)
                logger.info("Søknad sendt til sak. Status=${response.status}")
            } catch (e: RestClientResponseException) {
                logger.warn("Innsending til sak feilet. Responskode: {}, body: {}", e.statusCode, e.responseBodyAsString)
                throw IllegalStateException(
                    "Innsending til sak feilet. Status: " + e.statusCode +
                        ", body: " + e.responseBodyAsString,
                    e,
                )
            } catch (e: RestClientException) {
                throw IllegalStateException("Innsending til sak feilet.", e)
            }
        }

        fun hentFagsaknummerPåPersonident(personIdent: String): String {
            val uri = URI.create("$sakServiceUri/fagsaker")
            return runCatching {
                postForEntity<Ressurs<RestFagsak>>(uri, mapOf("personIdent" to personIdent))
            }.fold(
                onSuccess = { it.data?.id?.toString() ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av saksnummer fra ba-sak.", it, uri, personIdent) },
            )
        }

        fun hentRestFagsakDeltagerListe(
            personIdent: String,
            barnasIdenter: List<String> = emptyList(),
        ): List<RestFagsakDeltager> {
            val uri = URI.create("$sakServiceUri/fagsaker/sok/fagsakdeltagere")
            return runCatching {
                postForEntity<Ressurs<List<RestFagsakDeltager>>>(uri, RestSøkParam(personIdent, barnasIdenter))
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av fagsakdeltagere fra ba-sak.", it, uri, personIdent) },
            )
        }

        fun hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(
            personIdent: String,
        ): List<RestFagsakIdOgTilknyttetAktørId> {
            val uri = URI.create("$sakServiceUri/fagsaker/sok/fagsaker-hvor-person-er-deltaker")
            return runCatching {
                postForEntity<Ressurs<List<RestFagsakIdOgTilknyttetAktørId>>>(uri, RestPersonIdent(personIdent))
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av fagsakId og aktørId fra ba-sak.", it, uri, personIdent) },
            )
        }

        fun hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(
            personIdent: String,
        ): List<RestFagsakIdOgTilknyttetAktørId> {
            val uri = URI.create("$sakServiceUri/fagsaker/sok/fagsaker-hvor-person-mottar-lopende-ytelse")
            return runCatching {
                postForEntity<Ressurs<List<RestFagsakIdOgTilknyttetAktørId>>>(uri, RestPersonIdent(personIdent))
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av fagsakId og aktørId fra ba-sak.", it, uri, personIdent) },
            )
        }

        fun hentMinimalRestFagsak(fagsakId: Long): RestMinimalFagsak {
            val uri = URI.create("$sakServiceUri/fagsaker/minimal/$fagsakId")
            return runCatching {
                getForEntity<Ressurs<RestMinimalFagsak>>(uri)
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av RestFagsak fra ba-sak.", it, uri) },
            )
        }

        fun hentRestFagsak(fagsakId: Long): RestFagsak {
            val uri = URI.create("$sakServiceUri/fagsaker/$fagsakId")
            return runCatching {
                getForEntity<Ressurs<RestFagsak>>(uri)
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av RestFagsak fra ba-sak.", it, uri) },
            )
        }

        fun sendVedtakOmOvergangsstønadHendelseTilBaSak(personIdent: String) {
            val uri = URI.create("$sakServiceUri/overgangsstonad")
            logger.info("sender ident fra vedtak om overgangsstønad til {}", uri)
            try {
                val response = postForEntity<Ressurs<String>>(uri, PersonIdent(ident = personIdent))
                logger.info("Ident fra vedtak om overgangsstønad sendt til sak. Status=${response.status}")
            } catch (e: RestClientResponseException) {
                logger.warn("Innsending til sak feilet. Responskode: {}, body: {}", e.statusCode, e.responseBodyAsString)
                throw IllegalStateException("Innsending til sak feilet. Status: ${e.statusCode}, body: ${e.responseBodyAsString}", e)
            } catch (e: RestClientException) {
                throw IllegalStateException("Innsending til sak feilet.", e)
            }
        }

        fun opprettBehandling(
            kategori: BehandlingKategori,
            underkategori: BehandlingUnderkategori,
            søkersIdent: String,
            behandlingÅrsak: String,
            søknadMottattDato: LocalDateTime,
            behandlingType: BehandlingType,
            fagsakId: Long,
        ) {
            val uri = URI.create("$sakServiceUri/behandlinger")
            kotlin.runCatching {
                postForEntity<Ressurs<Any>>(
                    uri,
                    RestOpprettBehandlingBarnetrygdRequest(
                        kategori = kategori.name,
                        underkategori = underkategori.name,
                        søkersIdent = søkersIdent,
                        behandlingÅrsak = behandlingÅrsak,
                        søknadMottattDato = søknadMottattDato,
                        behandlingType = behandlingType,
                        fagsakId = fagsakId,
                    ),
                )
            }
                .onFailure {
                    throw IntegrasjonException("Feil ved opprettelse av behandling i ba-sak.", it, uri)
                }
        }
    }
