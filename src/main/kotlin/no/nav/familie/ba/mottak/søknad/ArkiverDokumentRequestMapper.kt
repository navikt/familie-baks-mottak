package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.kontrakter.felles.arkivering.Dokument
import no.nav.familie.kontrakter.felles.arkivering.FilType
import no.nav.familie.kontrakter.felles.arkivering.v2.ArkiverDokumentRequest

object ArkiverDokumentRequestMapper {

    fun toDto(dbSøknad: DBSøknad): ArkiverDokumentRequest {
        val søknad = dbSøknad.hentSøknad()

        val søknadsdokumentJson =
                Dokument(dbSøknad.søknadJson.toByteArray(), FilType.JSON, null, "hoveddokument", "BARNETRYGD_${søknad.søknadstype}")
        // TODO: Fjern dummy-pdf
        val søknadsdokumentPdf =
                Dokument("test123".toByteArray(), FilType.PDFA, null, "hoveddokument", "BARNETRYGD_${søknad.søknadstype}")
        val hoveddokumentvarianter = listOf(søknadsdokumentPdf, søknadsdokumentJson)
        return ArkiverDokumentRequest(dbSøknad.fnr, false, hoveddokumentvarianter)
    }
}