package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.ba.mottak.søknad.domene.DBVedlegg
import no.nav.familie.ba.mottak.søknad.domene.SøknadV7
import no.nav.familie.ba.mottak.søknad.domene.VersjonertSøknad
import no.nav.familie.kontrakter.ba.søknad.v7.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.felles.dokarkiv.Dokumenttype
import no.nav.familie.kontrakter.felles.dokarkiv.v2.ArkiverDokumentRequest
import no.nav.familie.kontrakter.felles.dokarkiv.v2.Dokument
import no.nav.familie.kontrakter.felles.dokarkiv.v2.Filtype

object ArkiverDokumentRequestMapper {

    fun toDto(
        dbSøknad: DBSøknad,
        versjonertSøknad: VersjonertSøknad,
        pdf: ByteArray,
        vedleggMap: Map<String, DBVedlegg>,
        pdfOriginalSpråk: ByteArray
    ): ArkiverDokumentRequest {

        val (søknadstype, dokumentasjon) = when (versjonertSøknad) {
            is SøknadV7 -> Pair(versjonertSøknad.søknad.søknadstype, versjonertSøknad.søknad.dokumentasjon)
        }

        val dokumenttype = when (søknadstype) {
            Søknadstype.ORDINÆR -> Dokumenttype.BARNETRYGD_ORDINÆR
            Søknadstype.UTVIDET -> Dokumenttype.BARNETRYGD_UTVIDET
            Søknadstype.IKKE_SATT -> Dokumenttype.BARNETRYGD_ORDINÆR
        }

        val søknadsdokumentJson =
            Dokument(
                dokument = dbSøknad.søknadJson.toByteArray(),
                filtype = Filtype.JSON,
                filnavn = null,
                tittel = "SØKNAD_${dokumenttype}_JSON",
                dokumenttype = dokumenttype
            )
        val søknadsdokumentPdf =
            Dokument(
                dokument = pdf,
                filtype = Filtype.PDFA,
                filnavn = null,
                tittel = when (dokumenttype) {
                    Dokumenttype.BARNETRYGD_UTVIDET -> "Søknad om utvidet barnetrygd"
                    Dokumenttype.BARNETRYGD_ORDINÆR -> "Søknad om ordinær barnetrygd"
                    else -> "Søknad om ordinær barnetrygd"
                },
                dokumenttype = dokumenttype
            )

        return ArkiverDokumentRequest(
            fnr = dbSøknad.fnr,
            forsøkFerdigstill = false,
            hoveddokumentvarianter = listOf(søknadsdokumentPdf, søknadsdokumentJson),
            vedleggsdokumenter = hentVedleggListeTilArkivering(dokumentasjon, vedleggMap, pdfOriginalSpråk),
            eksternReferanseId = dbSøknad.id.toString()
        )
    }

    private fun hentVedleggListeTilArkivering(
        dokumentasjon: List<Søknaddokumentasjon>,
        vedleggMap: Map<String, DBVedlegg>,
        pdfOriginalSpråk: ByteArray
    ): List<Dokument> {
        val vedlegg = mutableListOf<Dokument>()

        dokumentasjon.forEach { dokumentasjonskrav ->
            dokumentasjonskrav.opplastedeVedlegg.forEach { opplastaVedlegg ->
                vedleggMap.get(opplastaVedlegg.dokumentId)?.also { dbFil ->
                    vedlegg.add(
                        Dokument(
                            dokument = dbFil.data,
                            dokumenttype = Dokumenttype.BARNETRYGD_VEDLEGG,
                            filtype = Filtype.PDFA,
                            tittel = dokumentasjonsbehovTilTittel(opplastaVedlegg.tittel)
                        )
                    )
                }
            }
        }

        if (pdfOriginalSpråk.isNotEmpty()) {
            vedlegg.add(
                Dokument(
                    dokument = pdfOriginalSpråk,
                    dokumenttype = Dokumenttype.BARNETRYGD_VEDLEGG,
                    filtype = Filtype.PDFA,
                    tittel = "Søknad på originalt utfylt språk"
                )
            )
        }

        return vedlegg
    }

    private fun dokumentasjonsbehovTilTittel(dokumentasjonsbehov: Dokumentasjonsbehov): String {
        return when (dokumentasjonsbehov) {
            Dokumentasjonsbehov.ADOPSJON_DATO -> "Adopsjonsdato"
            Dokumentasjonsbehov.AVTALE_DELT_BOSTED -> "Avtale om delt bosted"
            Dokumentasjonsbehov.VEDTAK_OPPHOLDSTILLATELSE -> "Vedtak om oppholdstillatelse"
            Dokumentasjonsbehov.BEKREFTELSE_FRA_BARNEVERN -> "Bekreftelse fra barnevern"
            Dokumentasjonsbehov.BOR_FAST_MED_SØKER -> "Bor fast med søker"
            Dokumentasjonsbehov.SEPARERT_SKILT_ENKE -> "Dokumentasjon på separasjon, skilsmisse eller dødsfall"
            Dokumentasjonsbehov.MEKLINGSATTEST -> "Meklingsattest"
            Dokumentasjonsbehov.ANNEN_DOKUMENTASJON -> "" // Random dokumentasjon skal saksbehandler sette tittel på
        }
    }
}
