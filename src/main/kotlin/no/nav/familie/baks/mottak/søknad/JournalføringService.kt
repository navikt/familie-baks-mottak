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
import org.springframework.web.client.HttpClientErrorException

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
        val vedlegg = barnetrygdSøknadService.hentLagredeVedlegg(dbBarnetrygdSøknad)

        val arkiverDokumentRequest =
            ArkiverDokumentRequestMapper.toDto(
                dbBarnetrygdSøknad = dbBarnetrygdSøknad,
                versjonertBarnetrygdSøknad = dbBarnetrygdSøknad.hentVersjonertBarnetrygdSøknad(),
                pdf = pdf,
                vedleggMap = vedlegg,
                pdfOriginalSpråk = pdfOriginalSpråk,
            )
        arkiverSøknadHvisIkkeAlleredeArkivert(arkiverDokumentRequest)

        barnetrygdSøknadService.slettDBSøknadOgVedlegg(dbBarnetrygdSøknad)
        log.info("Søknad og vedlegg er slettet fra databasen etter journalføring")
    }

    fun journalførKontantstøtteSøknad(
        dbKontantstøtteSøknad: DBKontantstøtteSøknad,
        pdf: ByteArray,
        pdfOriginalSpråk: ByteArray = ByteArray(0),
    ) {
        val vedlegg = kontantstøtteSøknadService.hentLagredeDBKontantstøtteVedlegg(dbKontantstøtteSøknad)

        val arkiverDokumentRequest =
            ArkiverDokumentRequestMapper.toDto(
                dbKontantstøtteSøknad = dbKontantstøtteSøknad,
                versjonertSøknad = dbKontantstøtteSøknad.hentVersjonertKontantstøtteSøknad(),
                pdf = pdf,
                vedleggMap = vedlegg,
                pdfOriginalSpråk = pdfOriginalSpråk,
            )
        arkiverSøknadHvisIkkeAlleredeArkivert(arkiverDokumentRequest)

        kontantstøtteSøknadService.slettDBKontantstøtteSøknadOgVedlegg(dbKontantstøtteSøknad)
        log.info("Søknad og vedlegg er slettet fra databasen etter journalføring")
    }

    fun arkiverSøknad(arkiverDokumentRequest: ArkiverDokumentRequest): String {
        val arkiverDokumentResponse = dokarkivClient.arkiver(arkiverDokumentRequest)
        log.info("Søknaden har blitt journalført med journalpostid: ${arkiverDokumentResponse.journalpostId}")
        return arkiverDokumentResponse.journalpostId
    }

    private fun arkiverSøknadHvisIkkeAlleredeArkivert(arkiverDokumentRequest: ArkiverDokumentRequest) {
        try {
            arkiverSøknad(arkiverDokumentRequest)
        } catch (e: HttpClientErrorException.Conflict) {
            // Dokarkiv svarer 409 når søknaden allerede er journalført med samme eksternReferanseId
            log.error("409 conflict for eksternReferanseId=${arkiverDokumentRequest.eksternReferanseId} ved journalføring av søknad. Søknaden er allerede journalført")
            SECURE_LOGGER.error(
                "409 conflict for eksternReferanseId=${arkiverDokumentRequest.eksternReferanseId} ved journalføring av søknad ${e.responseBodyAsString}",
                e,
            )
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
        private val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
    }
}
