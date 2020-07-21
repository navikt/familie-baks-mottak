package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.kontrakter.felles.arkivering.Dokument
import no.nav.familie.kontrakter.felles.arkivering.FilType
import no.nav.familie.kontrakter.felles.arkivering.v2.ArkiverDokumentRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ArkiverDokumentRequestMapper {

    fun toDto(dbSøknad: DBSøknad, pdf: ByteArray): ArkiverDokumentRequest {
        val søknad = dbSøknad.hentSøknad()
        val dokumenttype = "BARNETRYGD_${søknad.søknadstype}"
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        log.info(dokumenttype)

        val søknadsdokumentJson =
                Dokument(dbSøknad.søknadJson.toByteArray(), FilType.JSON, null, "hoveddokument", dokumenttype)
        // TODO: Fiks pdf
        val søknadsdokumentPdf =
                Dokument(pdf, FilType.PDFA, null, "hoveddokument", dokumenttype)
        val hoveddokumentvarianter = listOf(søknadsdokumentPdf, søknadsdokumentJson)
        return ArkiverDokumentRequest(dbSøknad.fnr, false, hoveddokumentvarianter)
    }


}