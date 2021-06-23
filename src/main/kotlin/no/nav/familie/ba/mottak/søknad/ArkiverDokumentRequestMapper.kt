package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.ba.mottak.søknad.domene.DBVedlegg
import no.nav.familie.kontrakter.ba.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.Søknad
import no.nav.familie.kontrakter.felles.dokarkiv.v2.Dokument
import no.nav.familie.kontrakter.felles.dokarkiv.v2.Filtype
import no.nav.familie.kontrakter.felles.dokarkiv.v2.ArkiverDokumentRequest
import no.nav.familie.kontrakter.felles.dokarkiv.Dokumenttype

object ArkiverDokumentRequestMapper {

    fun toDto(dbSøknad: DBSøknad, pdf: ByteArray, vedleggMap: Map<String, DBVedlegg>): ArkiverDokumentRequest {
        val søknad = dbSøknad.hentSøknad()
        val dokumenttype = when (søknad.søknadstype) {
            Søknadstype.ORDINÆR -> Dokumenttype.BARNETRYGD_ORDINÆR
            Søknadstype.EØS -> Dokumenttype.BARNETRYGD_EØS
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
            vedleggsdokumenter = hentVedleggListeTilArkivering(dbSøknad.hentSøknad(), vedleggMap)
        )
    }

    private fun hentVedleggListeTilArkivering(søknad: Søknad, vedleggMap: Map<String, DBVedlegg>): List<Dokument> {
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
            Dokumentasjonsbehov.ANNEN_DOKUMENTASJON -> "" // Random dokumentasjon skal saksbehandler sette tittel på
        }
    }
}