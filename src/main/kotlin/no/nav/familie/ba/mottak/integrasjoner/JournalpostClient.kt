package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.ResponseEntity
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.URI

private val logger = LoggerFactory.getLogger(JournalpostClient::class.java)
private const val OAUTH2_CLIENT_CONFIG_KEY = "integrasjoner-clientcredentials"

@Component
class JournalpostClient @Autowired constructor(@param:Value("\${FAMILIE_INTEGRASJONER_API_URL}")
                                               private val integrasjonerServiceUri: URI,
                                               restTemplateBuilderMedProxy: RestTemplateBuilder,
                                               clientConfigurationProperties: ClientConfigurationProperties,
                                               oAuth2AccessTokenService: OAuth2AccessTokenService)
    : BaseService(OAUTH2_CLIENT_CONFIG_KEY,
                  restTemplateBuilderMedProxy,
                  clientConfigurationProperties,
                  oAuth2AccessTokenService) {

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun hentJournalpost(journalpostId: String): Journalpost {
        val uri = URI.create("$integrasjonerServiceUri/journalpost?journalpostId=$journalpostId")
        logger.info("henter journalpost med id {}", journalpostId)
        return try {
            val response: ResponseEntity<Ressurs<Journalpost>>? = getRequest(uri)
            response?.body?.data ?: error("Fant ikke journalpost")
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
                       val tema: String?,
                       val behandlingstema: String?,
                       val sak: Sak?,
                       val bruker: Bruker?,
                       val journalforendeEnhet: String?,
                       val kanal: String?,
                       val dokumenter: List<DokumentInfo>?)

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