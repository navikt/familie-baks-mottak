package no.nav.familie.ba.mottak.integrasjoner

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

private val logger = LoggerFactory.getLogger(SakService::class.java)
private const val OAUTH2_CLIENT_CONFIG_KEY = "ba-sak-clientcredentials"

@Component
class JournalpostService @Autowired constructor(@param:Value("\${FAMILIE_INTEGRASJONER_API_URL}")
                                                private val integrasjonerServiceUri: URI,
                                                restTemplateBuilderMedProxy: RestTemplateBuilder,
                                                clientConfigurationProperties: ClientConfigurationProperties,
                                                oAuth2AccessTokenService: OAuth2AccessTokenService)
    : BaseService(OAUTH2_CLIENT_CONFIG_KEY,
                  restTemplateBuilderMedProxy,
                  clientConfigurationProperties,
                  oAuth2AccessTokenService) {

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun hentJournalpost(journalpostId: String): Journalpost? {
        val uri = URI.create("$integrasjonerServiceUri/journalpost?journalpostId=$journalpostId")
        logger.info("henter journalpost med id {}", journalpostId)
        return try {
            val response: ResponseEntity<Journalpost>? = getRequest(uri)
            response?.body
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
                       val journalpostype: Journalposttype?,
                       val journalstatus: Journalstatus?,
                       val tema: Tema?,
                       val behandlingstema: String?,
                       val sak: Sak?,
                       val journalforendeEnhet: String?,
                       val kanal: Kanal?,
                       val dokumenter: List<DokumentInfo>?)

data class Sak(val arkivsaksnummer: String?,
               var arkivsaksystem: String?,
               val fagsakId: String?,
               val fagsaksystem: String?)

data class DokumentInfo(val tittel: String?,
                        val brevkode: String?,
                        val dokumentstatus: Dokumentstatus?,
                        val dokumentvarianter: List<Dokumentvariant>?)

data class Dokumentvariant(val variantformat: Variantformat?)

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

enum class Tema {
    AAR,
    AGR,
    BAR,
    BID,
    BIL,
    DAG,
    ENF,
    ERS,
    FAR,
    FEI,
    FOR,
    FOS,
    FUL,
    GEN,
    GRA,
    GRU,
    HEL,
    HJE,
    IAR,
    IND,
    KON,
    KTR,
    MED,
    MOB,
    OMS,
    OPA,
    OPP,
    PEN,
    PER,
    REH,
    REK,
    RPO,
    RVE,
    SAA,
    SAK,
    SAP,
    SER,
    SIK,
    STO,
    SUP,
    SYK,
    SYM,
    TIL,
    TRK,
    TRY,
    TSO,
    TSR,
    UFM,
    UFO,
    UKJ,
    VEN,
    YRA,
    YRK
}

enum class Kanal {
    ALTINN,
    EIA,
    NAV_NO,
    NAV_NO_UINNLOGGET,
    SKAN_NETS,
    SKAN_PEN,
    EESSI,
    EKST_OPPS,
    SENTRAL_UTSKRIFT,
    LOKAL_UTSKRIFT,
    SDP,
    TRYGDERETTEN,
    HELSENETTET,
    INGEN_DISTRIBUSJON,
    UKJENT
}

enum class Dokumentstatus {
    FERDIGSTILT,
    AVBRUTT,
    UNDER_REDIGERING,
    KASSERT
}

enum class Variantformat {
    ARKIV,
    FULLVERSJON,
    PRODUKSJON,
    PRODUKSJON_DLF,
    SLADDET,
    ORIGINAL
}