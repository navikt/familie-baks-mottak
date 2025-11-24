package no.nav.familie.baks.mottak.søknad.kontantstøtte.domene

import no.nav.familie.baks.mottak.søknad.ISøknaddokumentasjon
import no.nav.familie.baks.mottak.søknad.Søknadsvedlegg
import no.nav.familie.kontrakter.ks.søknad.v1.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ks.søknad.v1.Søknaddokumentasjon

data class KontantstøtteSøknaddokumentasjon(
    val søknaddokumentasjon: Søknaddokumentasjon,
) : ISøknaddokumentasjon {
    override val opplastedeVedlegg: List<Søknadsvedlegg> =
        søknaddokumentasjon.opplastedeVedlegg.map {
            Søknadsvedlegg(
                it.dokumentId,
                dokumentasjonsbehovTilTittel(it.tittel),
            )
        }

    private fun dokumentasjonsbehovTilTittel(dokumentasjonsbehov: Dokumentasjonsbehov): String =
        when (dokumentasjonsbehov) {
            Dokumentasjonsbehov.AVTALE_DELT_BOSTED -> "Avtale om delt bosted"

            Dokumentasjonsbehov.VEDTAK_OPPHOLDSTILLATELSE -> "Vedtak om oppholdstillatelse"

            Dokumentasjonsbehov.ADOPSJON_DATO -> "Adopsjonsdato"

            Dokumentasjonsbehov.BOR_FAST_MED_SØKER -> "Bor fast med søker"

            Dokumentasjonsbehov.ANNEN_DOKUMENTASJON -> ""

            // Random dokumentasjon skal saksbehandler sette tittel på
            Dokumentasjonsbehov.BEKREFTELESE_PÅ_BARNEHAGEPLASS -> "Bekreftelse på barnehageplass"
        }
}
