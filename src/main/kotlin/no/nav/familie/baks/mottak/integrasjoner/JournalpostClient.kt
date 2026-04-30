package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.getDataOrThrow
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.URI

private val logger = LoggerFactory.getLogger(JournalpostClient::class.java)

@Component
class JournalpostClient
    @Autowired
    constructor(
        @param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonerServiceUri: URI,
        @Qualifier("integrasjonerRestClient") private val restClient: RestClient,
    ) {
        fun hentJournalpost(journalpostId: String): Journalpost {
            val uri = URI.create("$integrasjonerServiceUri/journalpost?journalpostId=$journalpostId")
            logger.debug("henter journalpost med id {}", journalpostId)
            return try {
                restClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(object : ParameterizedTypeReference<Ressurs<Journalpost>>() {})!!
                    .getDataOrThrow()
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
