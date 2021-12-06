package no.nav.familie.ba.mottak.søknad

import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype

import no.nav.familie.kontrakter.ba.søknad.v5.Barn as BarnV5
import no.nav.familie.kontrakter.ba.søknad.v6.Barn
import no.nav.familie.kontrakter.ba.søknad.v4.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ba.søknad.v4.Søker as SøkerV4

import no.nav.familie.kontrakter.ba.søknad.v5.Søknad as SøknadV5
import no.nav.familie.kontrakter.ba.søknad.v6.Søknad as SøknadV6
import no.nav.familie.kontrakter.ba.søknad.SøknadAdresse
import no.nav.familie.kontrakter.ba.søknad.v4.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsfelt as SøknadsfeltV4
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsvedlegg
import no.nav.familie.kontrakter.ba.søknad.v5.RegistrertBostedType

fun <T>søknadsfelt(label: String, verdi: T): SøknadsfeltV4<T> {
    return SøknadsfeltV4(label = mapOf("nb" to label), verdi = mapOf("nb" to verdi))
}

object SøknadTestData {
    fun søker(): SøkerV4 {
        return SøkerV4(
            navn = søknadsfelt("navn", "Navn Navnessen"),
            ident = søknadsfelt("fødselsnummer", "1234578901"),
            statsborgerskap = søknadsfelt("statsborgerskap", listOf("NOR")),
            adresse = søknadsfelt("adresse", SøknadAdresse(
                    adressenavn = null,
                    postnummer = null,
                    husbokstav = null,
                    bruksenhetsnummer = null,
                    husnummer = null,
                    poststed = null
                )),
            sivilstand = søknadsfelt("sivilstand", SIVILSTANDTYPE.GIFT),
            spørsmål = mapOf(),
            nåværendeSamboer = null,
            tidligereSamboere = listOf()
        )
    }

    fun barnv5(): List<BarnV5> {
        return listOf(
            BarnV5(
                navn = søknadsfelt("Barnets fulle navn", "barn1"),
                ident = søknadsfelt("Fødselsnummer", "12345678999"),
                registrertBostedType = søknadsfelt("Skal ha samme adresse", RegistrertBostedType.REGISTRERT_ANNEN_ADRESSE),
                alder = søknadsfelt("alder", "4 år"),
                spørsmål = mapOf("andreForelderNavn" to søknadsfelt("navn", "Thor")),
            ),
            BarnV5(
                navn = søknadsfelt("Barnets fulle navn", "barn2"),
                ident = søknadsfelt("Fødselsnummer", "12345678987"),
                registrertBostedType = søknadsfelt("Skal ha samme adresse", RegistrertBostedType.REGISTRERT_ANNEN_ADRESSE),
                alder = søknadsfelt("alder", "1 år"),
                spørsmål = mapOf("andreForelderNavn" to søknadsfelt("navn", "Thor")),
            )
        )
    }

    fun barn(): List<Barn> {
        return listOf(
            Barn(
                navn = søknadsfelt("Barnets fulle navn", "barn1"),
                ident = søknadsfelt("Fødselsnummer", "12345678999"),
                registrertBostedType = søknadsfelt("Skal ha samme adresse", RegistrertBostedType.REGISTRERT_ANNEN_ADRESSE),
                alder = søknadsfelt("alder", "4 år"),
                spørsmål = mapOf(),
            ),
            Barn(
                navn = søknadsfelt("Barnets fulle navn", "barn2"),
                ident = søknadsfelt("Fødselsnummer", "12345678987"),
                registrertBostedType = søknadsfelt("Skal ha samme adresse", RegistrertBostedType.IKKE_FYLT_INN),
                alder = søknadsfelt("alder", "1 år"),
                spørsmål = mapOf(),
            ),
            Barn(
                navn = søknadsfelt("Barnets fulle navn", "barn3"),
                ident = søknadsfelt("Fødselsnummer", "12345678988"),
                registrertBostedType = søknadsfelt("Skal ha samme adresse", RegistrertBostedType.REGISTRERT_SOKERS_ADRESSE),
                alder = søknadsfelt("alder", "2 år"),
                spørsmål = mapOf(),
            )
        )
    }

    fun søknad(): SøknadV6 {
        return SøknadV6(
            søknadstype = Søknadstype.ORDINÆR,
            søker = søker(),
            barn = barn(),
            spørsmål = mapOf(),
            dokumentasjon = listOf(
                Søknaddokumentasjon(
                    dokumentasjonsbehov = Dokumentasjonsbehov.ANNEN_DOKUMENTASJON,
                    harSendtInn = false,
                    opplastedeVedlegg = listOf(
                        Søknadsvedlegg(
                            dokumentId = "en-slags-uuid",
                            navn = "IMG 1337.png",
                            tittel = Dokumentasjonsbehov.ANNEN_DOKUMENTASJON
                        )
                    ),
                    dokumentasjonSpråkTittel = mapOf("nb" to "Bekreftelse fra barnevernet")
                )
            ),
            originalSpråk = "nb",
            teksterUtenomSpørsmål = mapOf()
        )
    }

    fun tomv5søknad(): SøknadV5 {
        return SøknadV5(
            søknadstype = Søknadstype.ORDINÆR,
            søker = søker(),
            barn = barnv5(),
            spørsmål = mapOf(),
            dokumentasjon = listOf(
                Søknaddokumentasjon(
                    dokumentasjonsbehov = Dokumentasjonsbehov.ANNEN_DOKUMENTASJON,
                    harSendtInn = false,
                    opplastedeVedlegg = listOf(
                        Søknadsvedlegg(
                            dokumentId = "en-slags-uuid",
                            navn = "IMG 1337.png",
                            tittel = Dokumentasjonsbehov.ANNEN_DOKUMENTASJON
                        )
                    ),
                    dokumentasjonSpråkTittel = mapOf("nb" to "Bekreftelse fra barnevernet")
                )
            ),
            originalSpråk = "nb",
            teksterUtenomSpørsmål = mapOf()
        )
    }
}