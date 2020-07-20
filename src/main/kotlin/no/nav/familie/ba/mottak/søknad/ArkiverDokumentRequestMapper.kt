package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.config.DOKUMENTTYPE_VEDLEGG
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.kontrakter.felles.arkivering.Dokument
import no.nav.familie.kontrakter.felles.arkivering.FilType
import no.nav.familie.kontrakter.felles.arkivering.v2.ArkiverDokumentRequest

object ArkiverDokumentRequestMapper {

    fun toDto(dbSøknad: DBSøknad): ArkiverDokumentRequest {
        val søknadsdokumentJson =
                Dokument(dbSøknad.søknadJson.toByteArray(), FilType.JSON, null, "hoveddokument", dbSøknad.dokumenttype)
        val søknadsdokumentPdf =
                Dokument(dbSøknad.søknadPdf!!.bytes, FilType.PDFA, null, "hoveddokument", dbSøknad.dokumenttype)
        val hoveddokumentvarianter = listOf(søknadsdokumentPdf, søknadsdokumentJson)
        return ArkiverDokumentRequest(dbSøknad.fnr, false, hoveddokumentvarianter, mapVedlegg(vedlegg))
    }


    private fun tilDokument(vedlegg: Vedlegg): Dokument {
        return Dokument(dokument = vedlegg.innhold.bytes,
                        filType = FilType.PDFA,
                        tittel = vedlegg.tittel,
                        filnavn = vedlegg.navn,
                        dokumentType = DOKUMENTTYPE_VEDLEGG)
    }


}