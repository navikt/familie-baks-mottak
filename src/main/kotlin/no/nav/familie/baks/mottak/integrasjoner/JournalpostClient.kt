package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.getDataOrThrow
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
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

@Component
class JournalpostClient
    @Autowired
    constructor(
        @param:Value("\${FAMILIE_INTEGRASJONER_API_URL}")
        private val integrasjonerServiceUri: URI,
        @Qualifier("clientCredentials") restOperations: RestOperations,
    ) : AbstractRestClient(restOperations, "integrasjon.saf") {
        @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delayExpression = "\${retry.backoff.delay:5000}"))
        fun hentJournalpost(journalpostId: String): Journalpost {
            val uri = URI.create("$integrasjonerServiceUri/journalpost?journalpostId=$journalpostId")
            logger.debug("henter journalpost med id {}", journalpostId)
            return try {
                val response = getForEntity<Ressurs<Journalpost>>(uri)
                response.getDataOrThrow()
            } catch (e: RestClientResponseException) {
                logger.warn("Henting av journalpost feilet. Responskode: {}, body: {}", e.statusCode, e.responseBodyAsString)
                throw IllegalStateException(
                    "Henting av journalpost med id $journalpostId feilet. Status: " + e.statusCode +
                        ", body: " + e.responseBodyAsString,
                    e,
                )
            } catch (e: RestClientException) {
                throw IllegalStateException("Henting av journalpost med id $journalpostId feilet.", e)
            }
        }
    }

fun Journalpost.erKontantstøtteSøknad(): Boolean = dokumenter?.any { it.brevkode == "NAV 34-00.08" } ?: false

fun Journalpost.erBarnetrygdOrdinærSøknad(): Boolean = dokumenter?.any { it.brevkode == "NAV 33-00.07" } ?: false

fun Journalpost.erBarnetrygdUtvidetSøknad(): Boolean = dokumenter?.any { it.brevkode == "NAV 33-00.09" } ?: false

fun Journalpost.erBarnetrygdSøknad(): Boolean = erBarnetrygdOrdinærSøknad() || erBarnetrygdUtvidetSøknad()

fun Journalpost.hentHovedDokumentTittel(): String? {
    if (dokumenter.isNullOrEmpty()) error("Journalpost $journalpostId mangler dokumenter")
    return dokumenter!!.firstOrNull { it.brevkode != null }?.tittel
}
