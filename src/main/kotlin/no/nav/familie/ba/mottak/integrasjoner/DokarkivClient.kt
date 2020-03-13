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
        val uri = URI.create("$integrasjonUri/v2/$journalpostId")
        val request = TilknyttFagsakRequest(bruker = Bruker(idType = IdType.FNR, id = fnr),
                                            tema = "BAR",
                                            sak = Sak(fagsakId, "BA"))

        when (val response = utførRequest(uri, request)) {
            is Throwable ->
                throw IntegrasjonException("Oppdatering av journalpost $journalpostId med fagsak $fagsakId feilet", response, uri, null)
        }
    }

    fun ferdigstillJournalpost(journalpostId: String) {
        logger.info("Forsøker å ferdigstille journalpost $journalpostId")
        val uri = URI.create("$integrasjonUri/v2/$journalpostId/ferdigstill?journalfoerendeEnhet=9999")

        when (val response = utførRequest(uri)) {
            is Throwable ->
                throw IntegrasjonException("Ferdigstilling av journalpost $journalpostId feilet", response, uri, null)
        }
    }

    private fun utførRequest(uri: URI, request: Any = ""): Any {
        return Result.runCatching {
            putForEntity<Ressurs<Any>>(uri, request)
        }.fold(
            onSuccess = { response -> assertGyldig(response) },
            onFailure = { it }
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

