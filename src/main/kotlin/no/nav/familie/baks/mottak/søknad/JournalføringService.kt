package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.integrasjoner.DokarkivClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.kontrakter.felles.dokarkiv.v2.ArkiverDokumentRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class JournalføringService(
    private val dokarkivClient: DokarkivClient,
    private val barnetrygdSøknadService: BarnetrygdSøknadService,
    private val kontantstøtteSøknadService: KontantstøtteSøknadService,
) {
    fun journalførBarnetrygdSøknad(
        dbBarnetrygdSøknad: DBBarnetrygdSøknad,
        pdf: ByteArray,
        pdfOriginalSpråk: ByteArray = ByteArray(0),
    ) {
        if (dbBarnetrygdSøknad.journalpostId == null) {
            val vedlegg = barnetrygdSøknadService.hentLagredeVedlegg(dbBarnetrygdSøknad)

            val arkiverDokumentRequest =
                ArkiverDokumentRequestMapper.toDto(
                    dbBarnetrygdSøknad = dbBarnetrygdSøknad,
                    versjonertBarnetrygdSøknad = dbBarnetrygdSøknad.hentVersjonertBarnetrygdSøknad(),
                    pdf = pdf,
                    vedleggMap = vedlegg,
                    pdfOriginalSpråk = pdfOriginalSpråk,
                )
            val journalpostId: String = arkiverSøknad(arkiverDokumentRequest)
            val dbSøknadMedJournalpostId = dbBarnetrygdSøknad.copy(journalpostId = journalpostId)
            barnetrygdSøknadService.lagreDBSøknad(dbSøknadMedJournalpostId)
            log.info("Søknaden er journalført og lagret til database")

            barnetrygdSøknadService.slettLagredeVedlegg(dbBarnetrygdSøknad)
            log.info("Vedlegg for søknad slettet fra database etter journalføring")
        } else {
            log.warn("Søknaden har allerede blitt journalført med journalpostId: ${dbBarnetrygdSøknad.journalpostId}")
        }
    }

    fun journalførKontantstøtteSøknad(
        dbKontantstøtteSøknad: DBKontantstøtteSøknad,
        pdf: ByteArray,
        pdfOriginalSpråk: ByteArray = ByteArray(0),
    ) {
        if (dbKontantstøtteSøknad.journalpostId == null) {
            val vedlegg = kontantstøtteSøknadService.hentLagredeDBKontantstøtteVedlegg(dbKontantstøtteSøknad)

            val arkiverDokumentRequest =
                ArkiverDokumentRequestMapper.toDto(
                    dbKontantstøtteSøknad = dbKontantstøtteSøknad,
                    versjonertSøknad = dbKontantstøtteSøknad.hentVersjonertKontantstøtteSøknad(),
                    pdf = pdf,
                    vedleggMap = vedlegg,
                    pdfOriginalSpråk = pdfOriginalSpråk,
                )
            val journalpostId: String = arkiverSøknad(arkiverDokumentRequest)
            val dbSøknadMedJournalpostId = dbKontantstøtteSøknad.copy(journalpostId = journalpostId)
            kontantstøtteSøknadService.lagreDBKontantstøtteSøknad(dbSøknadMedJournalpostId)
            log.info("Søknaden er journalført og lagret til database")

            kontantstøtteSøknadService.slettLagredeDBKontantstøtteVedlegg(dbKontantstøtteSøknad)
            log.info("Vedlegg for søknad slettet fra database etter journalføring")
        } else {
            log.warn("Søknaden har allerede blitt journalført med journalpostId: ${dbKontantstøtteSøknad.journalpostId}")
        }
    }

    fun arkiverSøknad(arkiverDokumentRequest: ArkiverDokumentRequest): String {
        val arkiverDokumentResponse = dokarkivClient.arkiver(arkiverDokumentRequest)
        log.info("Søknaden har blitt journalført med journalpostid: ${arkiverDokumentResponse.journalpostId}")
        return arkiverDokumentResponse.journalpostId
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
