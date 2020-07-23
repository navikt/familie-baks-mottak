package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.kontrakter.felles.arkivering.Dokument
import no.nav.familie.kontrakter.felles.arkivering.FilType
import no.nav.familie.kontrakter.felles.arkivering.v2.ArkiverDokumentRequest

object ArkiverDokumentRequestMapper {

    fun toDto(dbSøknad: DBSøknad, pdf: ByteArray): ArkiverDokumentRequest {
        val søknad = dbSøknad.hentSøknad()
        val dokumenttype = "BARNETRYGD_${søknad.søknadstype.verdi}"

        val søknadsdokumentJson =
                Dokument(dbSøknad.søknadJson.toByteArray(), FilType.JSON, null, "SØKNAD_${dokumenttype}_JSON", dokumenttype)
        val søknadsdokumentPdf =
                Dokument(pdf, FilType.PDFA, null, "SØKNAD_${dokumenttype}_PDF", dokumenttype)
        val hoveddokumentvarianter = listOf(søknadsdokumentPdf, søknadsdokumentJson)
        return ArkiverDokumentRequest(dbSøknad.fnr, false, hoveddokumentvarianter)
    }
}