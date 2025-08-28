package no.nav.familie.baks.mottak.søknad.barnetrygd.domene

import no.nav.familie.baks.mottak.søknad.ISøknaddokumentasjon
import no.nav.familie.baks.mottak.søknad.Søknadsvedlegg
import no.nav.familie.kontrakter.felles.søknad.BaFellesDokumentasjonsbehov
import no.nav.familie.kontrakter.felles.søknad.BaSøknaddokumentasjon

data class BarnetrygdSøknaddokumentasjon(
    val søknaddokumentasjon: BaSøknaddokumentasjon,
) : ISøknaddokumentasjon {
    override val opplastedeVedlegg: List<Søknadsvedlegg> =
        søknaddokumentasjon.opplastedeVedlegg.map {
            Søknadsvedlegg(
                it.dokumentId,
                dokumentasjonsbehovTilTittel(it.tittel.tilFellesDokumentasjonsbehov()),
            )
        }

    private fun dokumentasjonsbehovTilTittel(dokumentasjonsbehov: BaFellesDokumentasjonsbehov): String =
        when (dokumentasjonsbehov) {
            BaFellesDokumentasjonsbehov.AdopsjonDato -> "Adopsjonsdato"
            BaFellesDokumentasjonsbehov.AvtaleDeltBosted -> "Avtale om delt bosted"
            BaFellesDokumentasjonsbehov.VedtakOppholdstillatelse -> "Vedtak om oppholdstillatelse"
            BaFellesDokumentasjonsbehov.BekreftelseFraBarnevern -> "Bekreftelse fra barnevern"
            BaFellesDokumentasjonsbehov.BorFastMedSøker -> "Bor fast med søker"
            BaFellesDokumentasjonsbehov.SeparertSkiltEnke -> "Dokumentasjon på separasjon, skilsmisse eller dødsfall"
            BaFellesDokumentasjonsbehov.Meklingsattest -> "Meklingsattest"
            BaFellesDokumentasjonsbehov.AnnenDokumentasjon -> "" // Random dokumentasjon skal saksbehandler sette tittel på
        }
}
