package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.net.URI
import java.time.LocalDateTime

@Component
class KsSakClient
    @Autowired
    constructor(
        @param:Value("\${FAMILIE_KS_SAK_API_URL}") private val ksSakServiceUri: String,
        @Qualifier("clientCredentials") restOperations: RestOperations,
    ) : AbstractRestClient(restOperations, "integrasjon") {
        fun hentSaksnummer(personIdent: String): Long {
            val uri = URI.create("$ksSakServiceUri/fagsaker")
            return runCatching {
                postForEntity<Ressurs<RestFagsak>>(uri, mapOf("personIdent" to personIdent))
            }.fold(
                onSuccess = { it.data?.id ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
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
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, uri = uri, ident = personIdent) },
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

        fun opprettBehandlingIKsSak(
            kategori: String,
            søkersIdent: String,
            behandlingÅrsak: String,
            søknadMottattDato: LocalDateTime,
            type: String,
        ) {
            val uri = URI.create("$ksSakServiceUri/behandlinger")
            kotlin.runCatching {
                postForEntity<Ressurs<Any>>(
                    uri,
                    RestKsSakBehandlingRequest(
                        kategori,
                        søkersIdent,
                        behandlingÅrsak,
                        søknadMottattDato,
                        type,
                    ),
                )
            }
                .onFailure {
                    throw IntegrasjonException("Feil ved opprettelse av behandling i ks-sak.", it, uri)
                }
        }

        fun hentRestFagsakDeltagerListe(
            personIdent: String,
            barnasIdenter: List<String> = emptyList(),
        ): List<RestFagsakDeltager> {
            val uri = URI.create("$ksSakServiceUri/fagsaker/sok/fagsakdeltagere")
            return runCatching {
                postForEntity<Ressurs<List<RestFagsakDeltager>>>(uri, RestSøkParam(personIdent, barnasIdenter))
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, uri = uri, ident = personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av fagsakdeltagere fra ba-sak.", it, uri, personIdent) },
            )
        }
    }
