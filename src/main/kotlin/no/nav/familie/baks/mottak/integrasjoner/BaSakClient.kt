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
        @param:Value("\${FAMILIE_BA_SAK_API_URL}") private val baSakServiceUri: String,
        @Qualifier("clientCredentials") restOperations: RestOperations,
    ) : AbstractRestClient(restOperations, "integrasjon") {
        @Retryable(
            value = [RuntimeException::class],
            maxAttempts = 3,
            backoff = Backoff(delayExpression = "\${retry.backoff.delay:5000}"),
        )
        fun sendTilSak(nyBehandling: NyBehandling) {
            val uri = URI.create("$baSakServiceUri/behandlinger")
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

        @Retryable(
            value = [RuntimeException::class],
            maxAttempts = 3,
            backoff = Backoff(delayExpression = "60000"),
        )
        fun sendIdenthendelseTilBaSak(personIdent: PersonIdent) {
            val uri = URI.create("$baSakServiceUri/ident")
            try {
                val response = postForEntity<Ressurs<String>>(uri, personIdent)
                secureLogger.info("Identhendelse sendt til ba-sak for $personIdent. Status=${response.status}")
            } catch (e: RestClientResponseException) {
<<<<<<< Updated upstream
                logger.info("Innsending av identhendelse til sak feilet. Responskode: {}, body: {}", e.statusCode, e.responseBodyAsString)
                secureLogger.info("Innsending av identhendelse til sak feilet for $personIdent. Responskode: {}, body: {}", e.statusCode, e.responseBodyAsString)
                throw IllegalStateException(
                    "Innsending av identhendelse til sak feilet. Status: " + e.statusCode +
=======
                logger.info("Innsending av identhendelse til ba-sak feilet. Responskode: {}, body: {}", e.rawStatusCode, e.responseBodyAsString)
                secureLogger.info("Innsending av identhendelse til ba-sak feilet for $personIdent. Responskode: {}, body: {}", e.rawStatusCode, e.responseBodyAsString)
                throw IllegalStateException(
                    "Innsending av identhendelse til ba-sak feilet. Status: " + e.rawStatusCode +
>>>>>>> Stashed changes
                        ", body: " + e.responseBodyAsString,
                    e,
                )
            } catch (e: RestClientException) {
                secureLogger.warn("Innsending av identhendelse til ba-sak feilet for $personIdent", e)
                throw IllegalStateException("Innsending av identhendelse til ba-sak feilet.", e)
            }
        }

        fun hentSaksnummer(personIdent: String): String {
            val uri = URI.create("$baSakServiceUri/fagsaker")
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
            val uri = URI.create("$baSakServiceUri/fagsaker/sok/fagsakdeltagere")
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
            val uri = URI.create("$baSakServiceUri/fagsaker/sok/fagsaker-hvor-person-er-deltaker")
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
            val uri = URI.create("$baSakServiceUri/fagsaker/sok/fagsaker-hvor-person-mottar-lopende-ytelse")
            return runCatching {
                postForEntity<Ressurs<List<RestFagsakIdOgTilknyttetAktørId>>>(uri, RestPersonIdent(personIdent))
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri, personIdent) },
                onFailure = { throw IntegrasjonException("Feil ved henting av fagsakId og aktørId fra ba-sak.", it, uri, personIdent) },
            )
        }

        fun hentMinimalRestFagsak(fagsakId: Long): RestMinimalFagsak {
            val uri = URI.create("$baSakServiceUri/fagsaker/minimal/$fagsakId")
            return runCatching {
                getForEntity<Ressurs<RestMinimalFagsak>>(uri)
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av RestFagsak fra ba-sak.", it, uri) },
            )
        }

        fun hentRestFagsak(fagsakId: Long): RestFagsak {
            val uri = URI.create("$baSakServiceUri/fagsaker/$fagsakId")
            return runCatching {
                getForEntity<Ressurs<RestFagsak>>(uri)
            }.fold(
                onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
                onFailure = { throw IntegrasjonException("Feil ved henting av RestFagsak fra ba-sak.", it, uri) },
            )
        }

        fun sendVedtakOmOvergangsstønadHendelseTilBaSak(personIdent: String) {
            val uri = URI.create("$baSakServiceUri/overgangsstonad")
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
    }

data class RestPersonIdent(
    val personIdent: String,
)

data class RestFagsakIdOgTilknyttetAktørId(
    val aktørId: String,
    val fagsakId: Long,
)

data class RestMinimalFagsak(
    val id: Long,
    val behandlinger: List<RestVisningBehandling>,
)

class RestVisningBehandling(
    val behandlingId: Long,
    val opprettetTidspunkt: LocalDateTime,
    val kategori: BehandlingKategori,
    val underkategori: BehandlingUnderkategori?,
    val aktiv: Boolean,
    val årsak: String? = null,
    val type: String,
    val status: String,
    val resultat: String? = null,
    val vedtaksdato: LocalDateTime? = null,
)

data class RestFagsak(
    val id: Long,
    val behandlinger: List<RestUtvidetBehandling>,
)

data class RestUtvidetBehandling(
    val aktiv: Boolean,
    val arbeidsfordelingPåBehandling: RestArbeidsfordelingPåBehandling,
    val behandlingId: Long,
    val kategori: BehandlingKategori,
    val opprettetTidspunkt: LocalDateTime,
    val resultat: String,
    val steg: String,
    val type: String,
    val underkategori: BehandlingUnderkategori,
)

data class RestArbeidsfordelingPåBehandling(
    val behandlendeEnhetId: String,
)

enum class BehandlingKategori {
    EØS,
    NASJONAL,
}

enum class BehandlingUnderkategori {
    UTVIDET,
    ORDINÆR,
}

data class RestSøkParam(
    val personIdent: String,
    val barnasIdenter: List<String> = emptyList(),
)

data class RestFagsakDeltager(
    val ident: String,
    val rolle: FagsakDeltagerRolle,
    val fagsakId: Long,
    val fagsakStatus: FagsakStatus,
)

data class RestAnnullerFødsel(val barnasIdenter: List<String>, val tidligereHendelseId: String)

enum class FagsakDeltagerRolle {
    BARN,
    FORELDER,
    UKJENT,
}

enum class FagsakStatus {
    OPPRETTET,
    LØPENDE, // Har minst én behandling gjeldende for fremtidig utbetaling
    AVSLUTTET,
}
