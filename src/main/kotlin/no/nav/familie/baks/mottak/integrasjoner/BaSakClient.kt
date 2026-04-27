package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.domene.NyBehandling
import no.nav.familie.kontrakter.felles.PersonIdent
import no.nav.familie.kontrakter.felles.Ressurs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.time.LocalDateTime

private val logger = LoggerFactory.getLogger(BaSakClient::class.java)

@Component
class BaSakClient
    @Autowired
    constructor(
        @param:Value("\${FAMILIE_BA_SAK_API_URL}") private val sakServiceUri: String,
        @param:Value("\${NAIS_TOKEN_ENDPOINT}") private val tokenEndpoint: String,
        @param:Value("\${BA_SAK_SCOPE}") private val baSakScope: String,
    ) {
        private val restTemplate = RestTemplate()

        private fun hentToken(): String {
            val tokenRestTemplate = RestTemplate()
            val headers =
                HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_JSON
                }
            val body =
                mapOf(
                    "identity_provider" to "entra_id",
                    "target" to baSakScope,
                )

            @Suppress("UNCHECKED_CAST")
            val response: ResponseEntity<Map<*, *>> =
                tokenRestTemplate.exchange(
                    tokenEndpoint,
                    HttpMethod.POST,
                    HttpEntity(body, headers),
                    Map::class.java as Class<Map<*, *>>,
                )
            return response.body?.get("access_token") as? String
                ?: throw IllegalStateException("Fikk ikke access_token fra Texas (NAIS_TOKEN_ENDPOINT)")
        }

        private fun lagHeaders(): HttpHeaders =
            HttpHeaders().apply {
                setBearerAuth(hentToken())
                contentType = MediaType.APPLICATION_JSON
            }

        private fun <T : Any> post(
            uri: URI,
            body: Any,
            responseType: ParameterizedTypeReference<T>,
        ): T =
            restTemplate
                .exchange(uri.toString(), HttpMethod.POST, HttpEntity(body, lagHeaders()), responseType)
                .body
                ?: throw IllegalStateException("Tom respons fra $uri")

        private fun <T : Any> put(
            uri: URI,
            body: Any,
            responseType: ParameterizedTypeReference<T>,
        ): T =
            restTemplate
                .exchange(uri.toString(), HttpMethod.PUT, HttpEntity(body, lagHeaders()), responseType)
                .body
                ?: throw IllegalStateException("Tom respons fra $uri")

        private fun <T : Any> get(
            uri: URI,
            responseType: ParameterizedTypeReference<T>,
        ): T =
            restTemplate
                .exchange(uri.toString(), HttpMethod.GET, HttpEntity<Void>(lagHeaders()), responseType)
                .body
                ?: throw IllegalStateException("Tom respons fra $uri")

        fun sendTilSak(nyBehandling: NyBehandling) {
            val uri = URI.create("$sakServiceUri/behandlinger")
            logger.info("Sender søknad til {}", uri)
            try {
                val response = put(uri, nyBehandling, object : ParameterizedTypeReference<Ressurs<String>>() {})
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

        fun sendSvalbardtilleggTilBaSak(personIdent: String) {
            val uri = URI.create("$sakServiceUri/svalbardtillegg/vurder-svalbardtillegg")
            try {
                val response = post(uri, PersonIdent(ident = personIdent), object : ParameterizedTypeReference<Ressurs<String>>() {})
                logger.info("Ident for svalbardtillegg sendt til sak. Status=${response.status}")
            } catch (e: RestClientResponseException) {
                logger.warn("Send svalbardtillegg til sak feilet. Responskode: ${e.statusCode}, body: ${e.responseBodyAsString}")
                throw IllegalStateException("Send svalbardtillegg til sak feilet. Status: ${e.statusCode}, body: ${e.responseBodyAsString}", e)
            } catch (e: RestClientException) {
                throw IllegalStateException("Send svalbardtillegg til sak feilet.", e)
            }
        }

        fun sendFinnmarkstilleggTilBaSak(personIdent: String) {
            val uri = URI.create("$sakServiceUri/finnmarkstillegg/vurder-finnmarkstillegg")
            try {
                val response = post(uri, PersonIdent(ident = personIdent), object : ParameterizedTypeReference<Ressurs<String>>() {})
                logger.info("Ident for finnmarkstillegg sendt til sak. Status=${response.status}")
            } catch (e: RestClientResponseException) {
                logger.warn("Send finnmarkstillegg til sak feilet. Responskode: ${e.statusCode}, body: ${e.responseBodyAsString}")
                throw IllegalStateException("Send finnmarkstillegg til sak feilet. Status: ${e.statusCode}, body: ${e.responseBodyAsString}", e)
            } catch (e: RestClientException) {
                throw IllegalStateException("Send finnmarkstillegg til sak feilet.", e)
            }
        }

        fun hentFagsaknummerPåPersonident(personIdent: String): Long {
            val uri = URI.create("$sakServiceUri/fagsaker")
            return runCatching {
                post(uri, mapOf("personIdent" to personIdent), object : ParameterizedTypeReference<Ressurs<RestFagsakId>>() {})
            }.fold(
                onSuccess = { it.data?.id ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av saksnummer fra ba-sak.", it, uri, personIdent) },
            )
        }

        fun hentRestFagsakDeltagerListe(
            personIdent: String,
            barnasIdenter: List<String> = emptyList(),
        ): List<RestFagsakDeltager> {
            val uri = URI.create("$sakServiceUri/fagsaker/sok/fagsakdeltagere")
            return runCatching {
                post(uri, RestSøkParam(personIdent, barnasIdenter), object : ParameterizedTypeReference<Ressurs<List<RestFagsakDeltager>>>() {})
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
                post(uri, RestPersonIdent(personIdent), object : ParameterizedTypeReference<Ressurs<List<RestFagsakIdOgTilknyttetAktørId>>>() {})
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
                post(uri, RestPersonIdent(personIdent), object : ParameterizedTypeReference<Ressurs<List<RestFagsakIdOgTilknyttetAktørId>>>() {})
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av fagsakId og aktørId fra ba-sak.", it, uri, personIdent) },
            )
        }

        fun hentMinimalRestFagsak(fagsakId: Long): RestMinimalFagsak {
            val uri = URI.create("$sakServiceUri/fagsaker/minimal/$fagsakId")
            return runCatching {
                get(uri, object : ParameterizedTypeReference<Ressurs<RestMinimalFagsak>>() {})
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av RestFagsak fra ba-sak.", it, uri) },
            )
        }

        fun hentRestFagsak(fagsakId: Long): RestFagsak {
            val uri = URI.create("$sakServiceUri/fagsaker/$fagsakId")
            return runCatching {
                get(uri, object : ParameterizedTypeReference<Ressurs<RestFagsak>>() {})
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av RestFagsak fra ba-sak.", it, uri) },
            )
        }

        fun sendVedtakOmOvergangsstønadHendelseTilBaSak(personIdent: String) {
            val uri = URI.create("$sakServiceUri/overgangsstonad")
            logger.info("sender ident fra vedtak om overgangsstønad til {}", uri)
            try {
                val response = post(uri, PersonIdent(ident = personIdent), object : ParameterizedTypeReference<Ressurs<String>>() {})
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
            søknadsinfo: Søknadsinfo,
        ) {
            val uri = URI.create("$sakServiceUri/behandlinger")
            kotlin
                .runCatching {
                    post(
                        uri,
                        RestOpprettBehandlingBarnetrygdRequest(
                            kategori = kategori.name,
                            underkategori = underkategori.name,
                            søkersIdent = søkersIdent,
                            behandlingÅrsak = behandlingÅrsak,
                            søknadMottattDato = søknadMottattDato,
                            behandlingType = behandlingType,
                            fagsakId = fagsakId,
                            søknadsinfo = søknadsinfo,
                        ),
                        object : ParameterizedTypeReference<Ressurs<Any>>() {},
                    )
                }.onFailure {
                    throw IntegrasjonException("Feil ved opprettelse av behandling i ba-sak.", it, uri)
                }
        }

        fun hentFagsakForSkjermetBarn(personIdent: String): List<RestFagsakSkjermetBarn> {
            val uri = URI.create("$sakServiceUri/fagsaker/hent-fagsaker-paa-person")
            return runCatching {
                post(
                    uri,
                    RestHentFagsakerPåPersonRequest(
                        personIdent,
                        fagsakTyper = listOf("SKJERMET_BARN"),
                    ),
                    object : ParameterizedTypeReference<Ressurs<List<RestFagsakSkjermetBarn>>>() {},
                )
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av fagsak skjermet barn fra ba-sak.", it, uri) },
            )
        }
    }
