package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.getDataOrThrow
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

private val logger = LoggerFactory.getLogger(JournalpostClient::class.java)
private const val OAUTH2_CLIENT_CONFIG_KEY = "integrasjoner-clientcredentials"

@Component
class JournalpostClient @Autowired constructor(@param:Value("\${FAMILIE_INTEGRASJONER_API_URL}")
                                               private val integrasjonerServiceUri: URI,
                                               @Qualifier("clientCredentials") restOperations: RestOperations)
    : AbstractRestClient(restOperations, "integrasjon.saf") {

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun hentJournalpost(journalpostId: String): Journalpost {
        val uri = URI.create("$integrasjonerServiceUri/journalpost?journalpostId=$journalpostId")
        logger.debug("henter journalpost med id {}", journalpostId)
        return try {
            val response = getForEntity<Ressurs<Journalpost>>(uri)
            response?.getDataOrThrow()
        } catch (e: RestClientResponseException) {
            logger.warn("Henting av journalpost feilet. Responskode: {}, body: {}", e.rawStatusCode, e.responseBodyAsString)
            throw IllegalStateException("Henting av journalpost med id $journalpostId feilet. Status: " + e.rawStatusCode
                                        + ", body: " + e.responseBodyAsString, e)
        } catch (e: RestClientException) {
            throw IllegalStateException("Henting av journalpost med id $journalpostId feilet.", e)
        }
    }
}

data class Journalpost(val journalpostId: String,
                       val journalposttype: Journalposttype,
                       val journalstatus: Journalstatus,
                       val tema: String? = null,
                       val behandlingstema: String? = null,
                       val sak: Sak? = null,
                       val bruker: Bruker? = null,
                       val journalforendeEnhet: String? = null,
                       val kanal: String? = null,
                       val dokumenter: List<DokumentInfo>? = null)

data class Sak(val arkivsaksnummer: String?,
               var arkivsaksystem: String?,
               val fagsakId: String?,
               val fagsaksystem: String?)

data class Bruker(val id: String,
                  val type: BrukerIdType)

data class DokumentInfo(val tittel: String?,
                        val brevkode: String?,
                        val dokumentstatus: Dokumentstatus?,
                        val dokumentvarianter: List<Dokumentvariant>?)

data class Dokumentvariant(val variantformat: String)

enum class Journalposttype {
    I,
    U,
    N
}

enum class Journalstatus {
    MOTTATT,
    JOURNALFOERT,
    FERDIGSTILT,
    EKSPEDERT,
    UNDER_ARBEID,
    FEILREGISTRERT,
    UTGAAR,
    AVBRUTT,
    UKJENT_BRUKER,
    RESERVERT,
    OPPLASTING_DOKUMENT,
    UKJENT
}

enum class Dokumentstatus {
    FERDIGSTILT,
    AVBRUTT,
    UNDER_REDIGERING,
    KASSERT
}

enum class BrukerIdType {
    AKTOERID,
    FNR,
    ORGNR
}