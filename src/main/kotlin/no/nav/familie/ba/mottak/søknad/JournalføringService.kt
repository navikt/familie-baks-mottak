package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.integrasjoner.DokarkivClient
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.ba.mottak.søknad.domene.DBVedlegg
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class JournalføringService(
    private val dokarkivClient: DokarkivClient,
    private val søknadService: SøknadService
) {

    fun journalførSøknad(dbSøknad: DBSøknad, pdf: ByteArray, pdfOriginalSpråk: ByteArray = ByteArray(0)) {
        if (dbSøknad.journalpostId == null) {
            val vedlegg = søknadService.hentLagredeVedlegg(dbSøknad)

            val journalpostId: String = arkiverSøknad(dbSøknad, pdf, vedlegg, pdfOriginalSpråk)
            val dbSøknadMedJournalpostId = dbSøknad.copy(journalpostId = journalpostId)
            søknadService.lagreDBSøknad(dbSøknadMedJournalpostId)
            log.info("Søknaden er journalført og lagret til database")

            søknadService.slettLagredeVedlegg(dbSøknad)
            log.info("Vedlegg for søknad slettet fra database etter journalføring")
        } else {
            log.warn("Søknaden har allerede blitt journalført med journalpostId: ${dbSøknad.journalpostId}")
        }
    }

    fun arkiverSøknad(dbSøknad: DBSøknad, pdf: ByteArray, vedlegg: Map<String, DBVedlegg>, pdfOriginalSpråk: ByteArray = ByteArray(0)): String {
        val arkiverDokumentRequest = ArkiverDokumentRequestMapper.toDto(
            dbSøknad = dbSøknad,
            versjonertSøknad = dbSøknad.hentVersjonertSøknad(),
            pdf = pdf,
            vedleggMap = vedlegg,
            pdfOriginalSpråk = pdfOriginalSpråk
        )
        val arkiverDokumentResponse = dokarkivClient.arkiver(arkiverDokumentRequest)
        log.info("Søknaden har blitt journalført med journalpostid: ${arkiverDokumentResponse.journalpostId}")
        return arkiverDokumentResponse.journalpostId
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
