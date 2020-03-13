package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.net.URI

private val logger = LoggerFactory.getLogger(DokarkivClient::class.java)

@Component
class DokarkivClient @Autowired constructor(@param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonUri: URI,
                                           @Qualifier("clientCredentials") restOperations: RestOperations)
    : AbstractRestClient(restOperations, "integrasjon") {

    fun oppdaterJournalpostSak(journalpostId: String, fagsakId: String, fnr: String) {
        logger.info("Oppdaterer journalpost $journalpostId med fagsaktilknytning $fagsakId ")
        val uri = URI.create("$integrasjonUri/$journalpostId")
        val request = TilknyttFagsakRequest(bruker = Bruker(idType = IdType.FNR, id = fnr),
                                            tema = "BAR",
                                            sak = Sak(fagsakId, "BA"))
        return Result.runCatching {
            putForEntity<Ressurs<OppdaterJournalpostResponse>>(uri, request)
        }.fold(
            onSuccess = { response -> assertGyldig(response) },
            onFailure = {
                throw IntegrasjonException("Oppdatering av journalpost $journalpostId med fagsak $fagsakId feilet", it, uri, null)
            }
        )
    }

    fun ferdigstillJournalpost(journalpostId: String) {
        logger.info("Forsøker å ferdigstille journalpost $journalpostId")
        val uri = URI.create("$integrasjonUri/$journalpostId/ferdigstill?journalfoerendeEnhet=9999")
        Result.runCatching {
            putForEntity<Ressurs<Map<String, String>>>(uri, "")
        }.fold(
            onSuccess = { response -> assertGyldig(response) },
            onFailure = { throw IntegrasjonException("Ferdigstilling av journalpost $journalpostId feilet", it, uri, null) }
        )
    }

    private inline fun <reified T : Any> assertGyldig(ressurs: Ressurs<T>?): T {
        return when {
            ressurs == null -> error("Finner ikke ressurs")
            ressurs.data == null -> error("Ressurs mangler data")
            ressurs.status != Ressurs.Status.SUKSESS -> error("Ressurs returnerer 2xx men har ressurs status failure")

            else -> ressurs.data!!
        }
    }

    data class OppdaterJournalpostResponse (
        val journalpostId: String
    )

    data class TilknyttFagsakRequest (val bruker: Bruker,
                                 val tema: String,
                                 val sak: Sak)

    data class Sak(val fagsakId: String,
              val fagsaksystem: String)

    data class Bruker(val idType: IdType,
                 val id: String)

    enum class IdType {
        FNR, ORGNR
    }
}

