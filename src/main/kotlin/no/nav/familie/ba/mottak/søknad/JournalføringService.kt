package no.nav.familie.ba.mottak.søknad


import no.nav.familie.ba.mottak.integrasjoner.DokarkivClient
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class JournalføringService(private val dokarkivClient: DokarkivClient,
                           private val søknadService: SøknadService) {

    fun journalførSøknad(søknadId: String, pdf: ByteArray) {
        val dbSøknad: DBSøknad = søknadService.hentDBSøknad(søknadId.toLong())
                ?: error("Fant ingen søknad i databasen med ID: $søknadId")
        if (dbSøknad.journalpostId == null) {
            val journalpostId: String = arkiverSøknad(dbSøknad, pdf)
            val dbSøknadMedJournalpostId = dbSøknad.copy(journalpostId = journalpostId)
            søknadService.lagreDBSøknad(dbSøknadMedJournalpostId)
        } else {
            log.warn("JournalpostId finnes allerede")
        }
    }

    fun arkiverSøknad(dbSøknad: DBSøknad, pdf: ByteArray): String {
        val arkiverDokumentRequest = ArkiverDokumentRequestMapper.toDto(dbSøknad, pdf)
        val arkiverDokumentResponse = dokarkivClient.arkiver(arkiverDokumentRequest)
        log.info("Søknaden har blitt journalført med journalpostid: ${arkiverDokumentResponse.journalpostId}")
        return arkiverDokumentResponse.journalpostId
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}