package no.nav.familie.baks.mottak.søknad

import no.nav.familie.kontrakter.ba.søknad.v1.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ba.søknad.v1.SøknadAdresse
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.v5.RegistrertBostedType
import no.nav.familie.kontrakter.ba.søknad.v7.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.v7.Søknadsvedlegg
import no.nav.familie.kontrakter.ba.søknad.v9.BarnetrygdSøknad
import no.nav.familie.kontrakter.felles.søknad.Søknadsfelt
import no.nav.familie.kontrakter.ba.søknad.v8.Barn as BarnV8
import no.nav.familie.kontrakter.ba.søknad.v8.Søker as SøkerV8

fun <T> søknadsfelt(
    label: String,
    verdi: T,
): Søknadsfelt<T> = Søknadsfelt(label = mapOf("nb" to label), verdi = mapOf("nb" to verdi))

object SøknadTestData {
    fun lagSøker(fnr: String = "21234578901"): SøkerV8 =
        SøkerV8(
            harEøsSteg = true,
            navn = søknadsfelt("navn", "Navn Navnessen"),
            ident = søknadsfelt("fødselsnummer", fnr),
            statsborgerskap = søknadsfelt("statsborgerskap", listOf("NOR")),
            adressebeskyttelse = false,
            adresse =
            søknadsfelt(
                "adresse",
                SøknadAdresse(
                    adressenavn = null,
                    postnummer = null,
                    husbokstav = null,
                    bruksenhetsnummer = null,
                    husnummer = null,
                    poststed = null,
                ),
            ),
            sivilstand = søknadsfelt("sivilstand", SIVILSTANDTYPE.GIFT),
            spørsmål = mapOf(),
            nåværendeSamboer = null,
            tidligereSamboere = listOf(),
            arbeidsperioderUtland = listOf(),
        )

    fun lagBarn(fnr: String = "12345678999"): BarnV8 =

        BarnV8(
            harEøsSteg = true,
            navn = søknadsfelt("Barnets fulle navn", "barn1"),
            ident = søknadsfelt("Fødselsnummer", fnr),
            registrertBostedType = søknadsfelt("Skal ha samme adresse", RegistrertBostedType.REGISTRERT_ANNEN_ADRESSE),
            alder = søknadsfelt("alder", "4 år"),
            spørsmål = mapOf(),
            utenlandsperioder = listOf(),
            eøsBarnetrygdsperioder = listOf(),
        )

    fun barnetrygdSøknad(
        søker: SøkerV8 = lagSøker(),
        barn: List<BarnV8> = listOf(lagBarn()),
    ): BarnetrygdSøknad =
        BarnetrygdSøknad(
            antallEøsSteg = 3,
            kontraktVersjon = 9,
            søknadstype = Søknadstype.ORDINÆR,
            søker = søker,
            barn = barn,
            spørsmål = mapOf(),
            dokumentasjon =
            listOf(
                Søknaddokumentasjon(
                    dokumentasjonsbehov = Dokumentasjonsbehov.ANNEN_DOKUMENTASJON,
                    harSendtInn = false,
                    opplastedeVedlegg =
                    listOf(
                        Søknadsvedlegg(
                            dokumentId = "en-slags-uuid",
                            navn = "IMG 1337.png",
                            tittel = Dokumentasjonsbehov.ANNEN_DOKUMENTASJON,
                        ),
                    ),
                    dokumentasjonSpråkTittel = mapOf("nb" to "Bekreftelse fra barnevernet"),
                ),
            ),
            originalSpråk = "nb",
            teksterUtenomSpørsmål = mapOf(),
            finnesPersonMedAdressebeskyttelse = false,
        )
}
