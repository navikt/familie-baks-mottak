package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.domene.NyBehandling
import no.nav.familie.kontrakter.felles.PersonIdent
import no.nav.familie.kontrakter.felles.Ressurs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.URI
import java.time.LocalDateTime

private val logger = LoggerFactory.getLogger(BaSakClient::class.java)

@Component
class BaSakClient
    @Autowired
    constructor(
        @param:Value("\${FAMILIE_BA_SAK_API_URL}") private val sakServiceUri: String,
        @param:Value("\${BA_SAK_SCOPE}") private val baSakScope: String,
        texasRestClientFactory: TexasRestClientFactory,
    ) {
        private val restClient = texasRestClientFactory.create(baSakScope)

        fun sendTilSak(nyBehandling: NyBehandling) {
            val uri = URI.create("$sakServiceUri/behandlinger")
            logger.info("Sender søknad til {}", uri)
            try {
                val response =
                    restClient
                        .put()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(nyBehandling)
                        .retrieve()
                        .body(object : ParameterizedTypeReference<Ressurs<String>>() {})!!
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
                val response =
                    restClient
                        .post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(PersonIdent(ident = personIdent))
                        .retrieve()
                        .body(object : ParameterizedTypeReference<Ressurs<String>>() {})!!
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
                val response =
                    restClient
                        .post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(PersonIdent(ident = personIdent))
                        .retrieve()
                        .body(object : ParameterizedTypeReference<Ressurs<String>>() {})!!
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
                restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapOf("personIdent" to personIdent))
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Ressurs<RestFagsakId>>() {})!!
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
                restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(RestSøkParam(personIdent, barnasIdenter))
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Ressurs<List<RestFagsakDeltager>>>() {})!!
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
                restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(RestPersonIdent(personIdent))
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Ressurs<List<RestFagsakIdOgTilknyttetAktørId>>>() {})!!
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
                restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(RestPersonIdent(personIdent))
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Ressurs<List<RestFagsakIdOgTilknyttetAktørId>>>() {})!!
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av fagsakId og aktørId fra ba-sak.", it, uri, personIdent) },
            )
        }

        fun hentMinimalRestFagsak(fagsakId: Long): RestMinimalFagsak {
            val uri = URI.create("$sakServiceUri/fagsaker/minimal/$fagsakId")
            return runCatching {
                restClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Ressurs<RestMinimalFagsak>>() {})!!
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av RestFagsak fra ba-sak.", it, uri) },
            )
        }

        fun hentRestFagsak(fagsakId: Long): RestFagsak {
            val uri = URI.create("$sakServiceUri/fagsaker/$fagsakId")
            return runCatching {
                restClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Ressurs<RestFagsak>>() {})!!
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av RestFagsak fra ba-sak.", it, uri) },
            )
        }

        fun sendVedtakOmOvergangsstønadHendelseTilBaSak(personIdent: String) {
            val uri = URI.create("$sakServiceUri/overgangsstonad")
            logger.info("sender ident fra vedtak om overgangsstønad til {}", uri)
            try {
                val response =
                    restClient
                        .post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(PersonIdent(ident = personIdent))
                        .retrieve()
                        .body(object : ParameterizedTypeReference<Ressurs<String>>() {})!!
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
                    restClient
                        .post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
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
                        ).retrieve()
                        .body(object : ParameterizedTypeReference<Ressurs<Any>>() {})
                }.onFailure {
                    throw IntegrasjonException("Feil ved opprettelse av behandling i ba-sak.", it, uri)
                }
        }

        fun hentFagsakForSkjermetBarn(personIdent: String): List<RestFagsakSkjermetBarn> {
            val uri = URI.create("$sakServiceUri/fagsaker/hent-fagsaker-paa-person")
            return runCatching {
                restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        RestHentFagsakerPåPersonRequest(
                            personIdent,
                            fagsakTyper = listOf("SKJERMET_BARN"),
                        ),
                    ).retrieve()
                    .body(object : ParameterizedTypeReference<Ressurs<List<RestFagsakSkjermetBarn>>>() {})!!
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av fagsak skjermet barn fra ba-sak.", it, uri) },
            )
        }
    }
