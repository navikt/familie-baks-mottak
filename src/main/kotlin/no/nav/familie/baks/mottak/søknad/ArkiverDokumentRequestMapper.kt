package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknaddokumentasjon
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBVedlegg
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.Vedlegg
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstotteVedlegg
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.StøttetVersjonertBarnetrygdSøknad
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV8
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV9
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.felles.dokarkiv.Dokumenttype
import no.nav.familie.kontrakter.felles.dokarkiv.v2.ArkiverDokumentRequest
import no.nav.familie.kontrakter.felles.dokarkiv.v2.Dokument
import no.nav.familie.kontrakter.felles.dokarkiv.v2.Filtype
import no.nav.familie.kontrakter.ks.søknad.StøttetVersjonertKontantstøtteSøknad
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV4
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV5

object ArkiverDokumentRequestMapper {
    private val KONTANTSTØTTE_ID_POSTFIX = "NAV_34-00.08"
    private val BARNETRYGD_ID_ORDINÆR_POSTFIX = "NAV_33-00.07"
    private val BARNETRYGD_ID_UTVIDET_POSTFIX = "NAV_33-00.09"

    fun toDto(
        dbBarnetrygdSøknad: DBBarnetrygdSøknad,
        versjonertBarnetrygdSøknad: StøttetVersjonertBarnetrygdSøknad,
        pdf: ByteArray,
        vedleggMap: Map<String, DBVedlegg>,
        pdfOriginalSpråk: ByteArray,
    ): ArkiverDokumentRequest {
        val (søknadstype, dokumentasjon) =
            when (versjonertBarnetrygdSøknad) {
                is VersjonertBarnetrygdSøknadV8 ->
                    Pair(
                        versjonertBarnetrygdSøknad.barnetrygdSøknad.søknadstype,
                        versjonertBarnetrygdSøknad.barnetrygdSøknad.dokumentasjon.map { BarnetrygdSøknaddokumentasjon(it) },
                    )

                is VersjonertBarnetrygdSøknadV9 ->
                    Pair(
                        versjonertBarnetrygdSøknad.barnetrygdSøknad.søknadstype,
                        versjonertBarnetrygdSøknad.barnetrygdSøknad.dokumentasjon.map { BarnetrygdSøknaddokumentasjon(it) },
                    )
            }

        val dokumenttype =
            when (søknadstype) {
                Søknadstype.ORDINÆR -> Dokumenttype.BARNETRYGD_ORDINÆR
                Søknadstype.UTVIDET -> Dokumenttype.BARNETRYGD_UTVIDET
                Søknadstype.IKKE_SATT -> Dokumenttype.BARNETRYGD_ORDINÆR
            }

        val søknadsdokumentJson =
            Dokument(
                dokument = dbBarnetrygdSøknad.søknadJson.toByteArray(),
                filtype = Filtype.JSON,
                filnavn = null,
                tittel = "SØKNAD_${dokumenttype}_JSON",
                dokumenttype = dokumenttype,
            )
        val søknadsdokumentPdf =
            Dokument(
                dokument = pdf,
                filtype = Filtype.PDFA,
                filnavn = null,
                tittel =
                    when (dokumenttype) {
                        Dokumenttype.BARNETRYGD_UTVIDET -> "Søknad om utvidet barnetrygd"
                        Dokumenttype.BARNETRYGD_ORDINÆR -> "Søknad om ordinær barnetrygd"
                        else -> "Søknad om ordinær barnetrygd"
                    },
                dokumenttype = dokumenttype,
            )

        return ArkiverDokumentRequest(
            fnr = dbBarnetrygdSøknad.fnr,
            forsøkFerdigstill = false,
            hoveddokumentvarianter = listOf(søknadsdokumentPdf, søknadsdokumentJson),
            vedleggsdokumenter =
                hentVedleggListeTilArkivering(
                    dokumentasjon,
                    vedleggMap,
                    pdfOriginalSpråk,
                    Dokumenttype.BARNETRYGD_VEDLEGG,
                ),
            eksternReferanseId = genererEksternReferanseId(dbBarnetrygdSøknad.id, dokumenttype),
        )
    }

    fun toDto(
        dbKontantstøtteSøknad: DBKontantstøtteSøknad,
        versjonertSøknad: StøttetVersjonertKontantstøtteSøknad,
        pdf: ByteArray,
        vedleggMap: Map<String, DBKontantstotteVedlegg>,
        pdfOriginalSpråk: ByteArray,
    ): ArkiverDokumentRequest {
        val dokumenttype = Dokumenttype.KONTANTSTØTTE_SØKNAD

        val dokumentasjon =
            when (versjonertSøknad) {
                is VersjonertKontantstøtteSøknadV4 ->
                    versjonertSøknad.kontantstøtteSøknad.dokumentasjon.map { KontantstøtteSøknaddokumentasjon(it) }

                is VersjonertKontantstøtteSøknadV5 ->
                    versjonertSøknad.kontantstøtteSøknad.dokumentasjon.map { KontantstøtteSøknaddokumentasjon(it) }
            }

        val søknadsdokumentJson =
            Dokument(
                dokument = dbKontantstøtteSøknad.søknadJson.toByteArray(),
                filtype = Filtype.JSON,
                filnavn = null,
                tittel = "SØKNAD_${dokumenttype}_JSON",
                dokumenttype = dokumenttype,
            )
        val søknadsdokumentPdf =
            Dokument(
                dokument = pdf,
                filtype = Filtype.PDFA,
                filnavn = null,
                tittel = "Søknad om kontantstøtte",
                dokumenttype = dokumenttype,
            )

        return ArkiverDokumentRequest(
            fnr = dbKontantstøtteSøknad.fnr,
            forsøkFerdigstill = false,
            hoveddokumentvarianter = listOf(søknadsdokumentPdf, søknadsdokumentJson),
            vedleggsdokumenter =
                hentVedleggListeTilArkivering(
                    dokumentasjon,
                    vedleggMap,
                    pdfOriginalSpråk,
                    Dokumenttype.KONTANTSTØTTE_VEDLEGG,
                ),
            eksternReferanseId = genererEksternReferanseId(dbKontantstøtteSøknad.id, dokumenttype),
        )
    }

    private fun hentVedleggListeTilArkivering(
        dokumentasjon: List<ISøknaddokumentasjon>,
        vedleggMap: Map<String, Vedlegg>,
        pdfOriginalSpråk: ByteArray,
        dokumenttype: Dokumenttype,
    ): List<Dokument> {
        val vedlegg = mutableListOf<Dokument>()

        dokumentasjon.forEach { dokumentasjonskrav ->
            dokumentasjonskrav.opplastedeVedlegg.forEach { opplastaVedlegg ->
                vedleggMap.get(opplastaVedlegg.dokumentId)?.also { dbFil ->
                    vedlegg.add(
                        Dokument(
                            dokument = dbFil.data,
                            dokumenttype = dokumenttype,
                            filtype = Filtype.PDFA,
                            tittel = opplastaVedlegg.tittel,
                        ),
                    )
                }
            }
        }

        if (pdfOriginalSpråk.isNotEmpty()) {
            vedlegg.add(
                Dokument(
                    dokument = pdfOriginalSpråk,
                    dokumenttype = dokumenttype,
                    filtype = Filtype.PDFA,
                    tittel = "Søknad på originalt utfylt språk",
                ),
            )
        }

        return vedlegg
    }

    private fun genererEksternReferanseId(
        id: Long,
        dokumenttype: Dokumenttype,
    ) = "${id}_${postfixForDokumenttype(dokumenttype)}"

    private fun postfixForDokumenttype(dokumenttype: Dokumenttype) =
        when (dokumenttype) {
            Dokumenttype.BARNETRYGD_ORDINÆR -> BARNETRYGD_ID_ORDINÆR_POSTFIX
            Dokumenttype.BARNETRYGD_UTVIDET -> BARNETRYGD_ID_UTVIDET_POSTFIX
            Dokumenttype.KONTANTSTØTTE_SØKNAD -> KONTANTSTØTTE_ID_POSTFIX
            else -> throw RuntimeException("Støtter ikke journalføring for dokumenttype: $dokumenttype")
        }
}

interface ISøknaddokumentasjon {
    val opplastedeVedlegg: List<Søknadsvedlegg>
}

data class Søknadsvedlegg(
    val dokumentId: String,
    val tittel: String,
)
