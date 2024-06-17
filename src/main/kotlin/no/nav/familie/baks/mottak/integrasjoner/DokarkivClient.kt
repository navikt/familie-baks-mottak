package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Fagsystem
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.dokarkiv.ArkiverDokumentResponse
import no.nav.familie.kontrakter.felles.dokarkiv.v2.ArkiverDokumentRequest
import no.nav.familie.kontrakter.felles.getDataOrThrow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.net.URI

private val logger = LoggerFactory.getLogger(DokarkivClient::class.java)

@Component
class DokarkivClient(
    @param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonUri: URI,
    @Qualifier("clientCredentials") restOperations: RestOperations,
) : AbstractRestClient(restOperations, "integrasjon") {
    fun oppdaterJournalpostSak(
        jp: Journalpost,
        fagsakId: String,
        tema: Tema,
    ) {
        logger.info("Oppdaterer journalpost ${jp.journalpostId} med fagsaktilknytning $fagsakId ")
        val uri = URI.create("$integrasjonUri/arkiv/v2/${jp.journalpostId}")

        val sak =
            when (tema) {
                Tema.BAR -> Sak(fagsakId, Fagsystem.BA.name)
                Tema.KON -> Sak(fagsakId, Fagsystem.KONT.name)
                else -> throw IllegalArgumentException("Tema $tema støtter ikke oppdatering av journalpost sak")
            }

        val request =
            TilknyttFagsakRequest(
                bruker = Bruker(idType = IdType.valueOf(jp.bruker!!.type.name), id = jp.bruker.id),
                tema = tema.name,
                sak = sak,
            )

        when (val response = utførRequest(uri, request)) {
            is Throwable -> throw IntegrasjonException(
                "Oppdatering av journalpost ${jp.journalpostId} med fagsak $fagsakId feilet",
                response,
                uri,
                jp.bruker.id,
            )
        }
    }

    fun arkiver(arkiverDokumentRequest: ArkiverDokumentRequest): ArkiverDokumentResponse {
        val uri = URI.create("$integrasjonUri/arkiv/v4")
        val response =
            postForEntity<Ressurs<ArkiverDokumentResponse>>(uri, arkiverDokumentRequest)
        return response.getDataOrThrow()
    }

    fun ferdigstillJournalpost(journalpostId: String) {
        logger.info("Forsøker å ferdigstille journalpost $journalpostId")
        val uri = URI.create("$integrasjonUri/arkiv/v2/$journalpostId/ferdigstill?journalfoerendeEnhet=9999")

        when (val response = utførRequest(uri)) {
            is Throwable -> throw IntegrasjonException("Ferdigstilling av journalpost $journalpostId feilet", response, uri)
        }
    }

    private fun utførRequest(
        uri: URI,
        request: Any = "",
    ): Any =
        Result
            .runCatching {
                putForEntity<Ressurs<Any>>(uri, request)
            }.fold(
                onSuccess = { response -> assertGyldig(response) },
                onFailure = { it },
            )

    private inline fun <reified T : Any> assertGyldig(ressurs: Ressurs<T>?): T =
        when {
            ressurs == null -> error("Finner ikke ressurs")
            ressurs.data == null -> error("Ressurs mangler data")
            ressurs.status != Ressurs.Status.SUKSESS -> error("Ressurs returnerer 2xx men har ressurs status failure")

            else -> ressurs.data!!
        }

    data class TilknyttFagsakRequest(
        val bruker: Bruker,
        val tema: String,
        val sak: Sak,
    )

    data class Sak(
        val fagsakId: String,
        val fagsaksystem: String,
    )

    data class Bruker(
        val idType: IdType,
        val id: String,
    )

    enum class IdType {
        FNR,
        ORGNR,
        AKTOERID,
    }
}
