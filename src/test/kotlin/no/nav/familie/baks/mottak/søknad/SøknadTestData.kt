package no.nav.familie.baks.mottak.søknad

import no.nav.familie.kontrakter.ba.søknad.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ba.søknad.SøknadAdresse
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.v5.RegistrertBostedType
import no.nav.familie.kontrakter.ba.søknad.v7.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.v7.Søknadsvedlegg
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsfelt as SøknadsfeltV4
import no.nav.familie.kontrakter.ba.søknad.v8.Barn as BarnV8
import no.nav.familie.kontrakter.ba.søknad.v8.Søker as SøkerV8
import no.nav.familie.kontrakter.ba.søknad.v8.Søknad as SøknadV8

fun <T> søknadsfelt(
    label: String,
    verdi: T,
): SøknadsfeltV4<T> = SøknadsfeltV4(label = mapOf("nb" to label), verdi = mapOf("nb" to verdi))

object SøknadTestData {
    private fun søkerV8(): SøkerV8 =
        SøkerV8(
            harEøsSteg = true,
            navn = søknadsfelt("navn", "Navn Navnessen"),
            ident = søknadsfelt("fødselsnummer", "1234578901"),
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

    private fun barnV8(): List<BarnV8> =
        listOf(
            BarnV8(
                harEøsSteg = true,
                navn = søknadsfelt("Barnets fulle navn", "barn1"),
                ident = søknadsfelt("Fødselsnummer", "12345678999"),
                registrertBostedType = søknadsfelt("Skal ha samme adresse", RegistrertBostedType.REGISTRERT_ANNEN_ADRESSE),
                alder = søknadsfelt("alder", "4 år"),
                spørsmål = mapOf(),
                utenlandsperioder = listOf(),
                eøsBarnetrygdsperioder = listOf(),
            ),
            BarnV8(
                harEøsSteg = false,
                navn = søknadsfelt("Barnets fulle navn", "barn2"),
                ident = søknadsfelt("Fødselsnummer", "12345678987"),
                registrertBostedType = søknadsfelt("Skal ha samme adresse", RegistrertBostedType.IKKE_FYLT_INN),
                alder = søknadsfelt("alder", "1 år"),
                spørsmål = mapOf(),
                utenlandsperioder = listOf(),
                eøsBarnetrygdsperioder = listOf(),
            ),
            BarnV8(
                harEøsSteg = true,
                navn = søknadsfelt("Barnets fulle navn", "barn3"),
                ident = søknadsfelt("Fødselsnummer", "12345678988"),
                registrertBostedType = søknadsfelt("Skal ha samme adresse", RegistrertBostedType.REGISTRERT_SOKERS_ADRESSE),
                alder = søknadsfelt("alder", "2 år"),
                spørsmål = mapOf(),
                utenlandsperioder = listOf(),
                eøsBarnetrygdsperioder = listOf(),
            ),
        )

    fun søknadV8(): SøknadV8 =
        SøknadV8(
            antallEøsSteg = 3,
            kontraktVersjon = 8,
            søknadstype = Søknadstype.ORDINÆR,
            søker = søkerV8(),
            barn = barnV8(),
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
        )
}
