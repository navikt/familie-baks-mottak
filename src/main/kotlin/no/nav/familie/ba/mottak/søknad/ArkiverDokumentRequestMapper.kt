package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.ba.mottak.søknad.domene.DBVedlegg
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.v4.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.v3.Dokumentasjonsbehov as DokumentasjonsbehovV3
import no.nav.familie.kontrakter.ba.søknad.v4.Søknad
import no.nav.familie.kontrakter.ba.søknad.v3.Søknad as SøknadV3
import no.nav.familie.kontrakter.felles.dokarkiv.v2.Dokument
import no.nav.familie.kontrakter.felles.dokarkiv.v2.Filtype
import no.nav.familie.kontrakter.felles.dokarkiv.v2.ArkiverDokumentRequest
import no.nav.familie.kontrakter.felles.dokarkiv.Dokumenttype

object ArkiverDokumentRequestMapper {

    fun toDto(dbSøknad: DBSøknad, pdf: ByteArray, vedleggMap: Map<String, DBVedlegg>, pdfOriginalSpråk: ByteArray): ArkiverDokumentRequest {
        val søknadstype = when (dbSøknad.hentSøknadVersjon()) {
            "v4" -> dbSøknad.hentSøknad().søknadstype
            else -> dbSøknad.hentSøknadV3().søknadstype
        }

        val dokumenttype = when (søknadstype) {
            Søknadstype.ORDINÆR -> Dokumenttype.BARNETRYGD_ORDINÆR
            Søknadstype.UTVIDET -> Dokumenttype.BARNETRYGD_UTVIDET
            else -> Dokumenttype.BARNETRYGD_ORDINÆR
        }

        val søknadsdokumentJson =
                Dokument(
                    dokument = dbSøknad.søknadJson.toByteArray(),
                    filtype = Filtype.JSON,
                    filnavn = null,
                    tittel = "SØKNAD_${dokumenttype}_JSON",
                    dokumenttype = dokumenttype)
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
        val hoveddokumentvarianter = listOf(søknadsdokumentPdf, søknadsdokumentJson)

        return ArkiverDokumentRequest(
            fnr = dbSøknad.fnr,
            forsøkFerdigstill = false,
            hoveddokumentvarianter = hoveddokumentvarianter,
            vedleggsdokumenter = when (dbSøknad.hentSøknadVersjon()) {
                "v4" -> hentVedleggListeTilArkivering(dbSøknad.hentSøknad(), vedleggMap, pdfOriginalSpråk)
                else -> hentVedleggListeTilArkivering(dbSøknad.hentSøknadV3(), vedleggMap)
            }
        )
    }

    private fun hentVedleggListeTilArkivering(søknad: Søknad, vedleggMap: Map<String, DBVedlegg>, pdfOriginalSpråk: ByteArray): List<Dokument> {
        val vedlegg = mutableListOf<Dokument>()

        søknad.dokumentasjon.forEach{ dokumentasjonskrav ->
            dokumentasjonskrav.opplastedeVedlegg.forEach{ opplastaVedlegg ->
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
            ))
        }

        return vedlegg
    }

    private fun hentVedleggListeTilArkivering(søknad: SøknadV3, vedleggMap: Map<String, DBVedlegg>): List<Dokument> {
        val vedlegg = mutableListOf<Dokument>()

        søknad.dokumentasjon.forEach{ dokumentasjonskrav ->
            dokumentasjonskrav.opplastedeVedlegg.forEach{ opplastaVedlegg ->
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

        return vedlegg
    }

    private fun dokumentasjonsbehovTilTittel(dokumentasjonsbehov: Dokumentasjonsbehov): String {
        return when(dokumentasjonsbehov) {
            Dokumentasjonsbehov.ADOPSJON_DATO -> "Adopsjonsdato"
            Dokumentasjonsbehov.AVTALE_DELT_BOSTED -> "Avtale om delt bosted"
            Dokumentasjonsbehov.VEDTAK_OPPHOLDSTILLATELSE -> "Vedtak om oppholdstillatelse"
            Dokumentasjonsbehov.BEKREFTELSE_FRA_BARNEVERN -> "Bekreftelse fra barnevern"
            Dokumentasjonsbehov.BOR_FAST_MED_SØKER -> "Bor fast med søker"
            Dokumentasjonsbehov.SEPARERT_SKILT_ENKE -> "Dokumentasjon på separasjon, skilsmisse eller dødsfall"
            Dokumentasjonsbehov.MEKLINGSATTEST -> "Meklingsattest"
            Dokumentasjonsbehov.EØS_SKJEMA -> "Tilleggsskjema for EØS"
            Dokumentasjonsbehov.ANNEN_DOKUMENTASJON -> "" // Random dokumentasjon skal saksbehandler sette tittel på
        }
    }

    private fun dokumentasjonsbehovTilTittel(dokumentasjonsbehov: DokumentasjonsbehovV3): String {
        return when(dokumentasjonsbehov) {
            DokumentasjonsbehovV3.ADOPSJON_DATO -> "Adopsjonsdato"
            DokumentasjonsbehovV3.AVTALE_DELT_BOSTED -> "Avtale om delt bosted"
            DokumentasjonsbehovV3.VEDTAK_OPPHOLDSTILLATELSE -> "Vedtak om oppholdstillatelse"
            DokumentasjonsbehovV3.BEKREFTELSE_FRA_BARNEVERN -> "Bekreftelse fra barnevern"
            DokumentasjonsbehovV3.BOR_FAST_MED_SØKER -> "Bor fast med søker"
            DokumentasjonsbehovV3.MEKLINGSATTEST -> "Meklingsattest"
            DokumentasjonsbehovV3.SEPARERT_SKILT_ENKE -> "Dokumentasjon på separasjon, skilsmisse eller dødsfall"
            DokumentasjonsbehovV3.ANNEN_DOKUMENTASJON -> "" // Random dokumentasjon skal saksbehandler sette tittel på
        }
    }
}