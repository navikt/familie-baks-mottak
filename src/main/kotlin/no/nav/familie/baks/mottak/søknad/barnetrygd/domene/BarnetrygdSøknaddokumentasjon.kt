package no.nav.familie.baks.mottak.søknad.barnetrygd.domene

import no.nav.familie.baks.mottak.søknad.ISøknaddokumentasjon
import no.nav.familie.baks.mottak.søknad.Søknadsvedlegg
import no.nav.familie.kontrakter.ba.søknad.v7.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon

data class BarnetrygdSøknaddokumentasjon(val søknaddokumentasjon: Søknaddokumentasjon) : ISøknaddokumentasjon {

    override val opplastedeVedlegg: List<Søknadsvedlegg> = søknaddokumentasjon.opplastedeVedlegg.map {
        Søknadsvedlegg(
            it.dokumentId,
            dokumentasjonsbehovTilTittel(it.tittel)
        )
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
