package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.texas.TexasRestClientFactory
import no.nav.familie.kontrakter.felles.Ressurs
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.body
import java.net.URI
import java.time.LocalDateTime

@Component
class KsSakClient
    @Autowired
    constructor(
        @param:Value("\${FAMILIE_KS_SAK_API_URL}") private val ksSakServiceUri: String,
        @param:Value("\${KS_SAK_SCOPE}") private val ksSakScope: String,
        texasRestClientFactory: TexasRestClientFactory,
    ) {
        private val restClient = texasRestClientFactory.lagMaskinRestKlient(ksSakScope)

        fun hentFagsaknummerPåPersonident(personIdent: String): Long {
            val uri = URI.create("$ksSakServiceUri/fagsaker")
            return runCatching {
                restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapOf("personIdent" to personIdent))
                    .retrieve()
                    .body<Ressurs<RestFagsak>>()!!
            }.fold(
                onSuccess = { it.data?.id ?: throw IntegrasjonException(it.melding, uri = uri, ident = personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av saksnummer fra ks-sak.", it, uri, personIdent) },
            )
        }

        fun hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(
            personIdent: String,
        ): List<RestFagsakIdOgTilknyttetAktørId> {
            val uri = URI.create("$ksSakServiceUri/fagsaker/sok/fagsaker-hvor-person-er-deltaker")
            return runCatching {
                restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(RestPersonIdent(personIdent))
                    .retrieve()
                    .body<Ressurs<List<RestFagsakIdOgTilknyttetAktørId>>>()!!
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, uri = uri, ident = personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av fagsakId og aktørId fra ks-sak.", it, uri, personIdent) },
            )
        }

        fun hentMinimalRestFagsak(fagsakId: Long): RestMinimalFagsak {
            val uri = URI.create("$ksSakServiceUri/fagsaker/minimal/$fagsakId")
            return runCatching {
                restClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body<Ressurs<RestMinimalFagsak>>()!!
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, uri = uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av RestFagsak fra ks-sak.", it, uri) },
            )
        }

        fun opprettBehandling(
            kategori: String,
            søkersIdent: String,
            behandlingÅrsak: String,
            søknadMottattDato: LocalDateTime,
            behandlingType: BehandlingType,
        ) {
            val uri = URI.create("$ksSakServiceUri/behandlinger")
            kotlin
                .runCatching {
                    restClient
                        .post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            RestOpprettBehandlingKontantstøtteRequest(
                                kategori = kategori,
                                søkersIdent = søkersIdent,
                                behandlingÅrsak = behandlingÅrsak,
                                søknadMottattDato = søknadMottattDato,
                                behandlingType = behandlingType,
                            ),
                        ).retrieve()
                        .body<Ressurs<Any>>()
                }.onFailure {
                    throw IntegrasjonException("Feil ved opprettelse av behandling i ks-sak.", it, uri)
                }
        }
    }
