package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.kontrakter.ba.Søknadstype
import no.nav.familie.kontrakter.felles.dokarkiv.v2.Dokument
import no.nav.familie.kontrakter.felles.dokarkiv.v2.Filtype
import no.nav.familie.kontrakter.felles.dokarkiv.v2.ArkiverDokumentRequest
import no.nav.familie.kontrakter.felles.dokarkiv.Dokumenttype

object ArkiverDokumentRequestMapper {

    fun toDto(dbSøknad: DBSøknad, pdf: ByteArray): ArkiverDokumentRequest {
        val søknad = dbSøknad.hentSøknad()
        val dokumenttype = when (søknad.søknadstype) {
            Søknadstype.ORDINÆR -> Dokumenttype.BARNETRYGD_ORDINÆR
            Søknadstype.EØS -> Dokumenttype.BARNETRYGD_EØS
            Søknadstype.UTVIDET -> Dokumenttype.BARNETRYGD_UTVIDET
            else -> Dokumenttype.BARNETRYGD_ORDINÆR
        }

        val søknadsdokumentJson =
                Dokument(dbSøknad.søknadJson.toByteArray(), Filtype.JSON, null, "SØKNAD_${dokumenttype}_JSON", dokumenttype)
        val søknadsdokumentPdf =
                Dokument(pdf, Filtype.PDFA, null, "SØKNAD_${dokumenttype}_PDF", dokumenttype)
        val hoveddokumentvarianter = listOf(søknadsdokumentPdf, søknadsdokumentJson)
        return ArkiverDokumentRequest(dbSøknad.fnr, false, hoveddokumentvarianter)
    }
}