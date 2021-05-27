package no.nav.familie.ba.mottak.søknad

import no.nav.familie.kontrakter.ba.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.Barn
import no.nav.familie.kontrakter.ba.søknad.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ba.søknad.Søker
import no.nav.familie.kontrakter.ba.søknad.Søknad
import no.nav.familie.kontrakter.ba.søknad.SøknadAdresse
import no.nav.familie.kontrakter.ba.søknad.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.Søknadsfelt
import no.nav.familie.kontrakter.ba.søknad.Søknadsvedlegg

object SøknadTestData {
    fun søker(): Søker {
        return Søker(
                navn = Søknadsfelt("navn", "Navn Navnessen"),
                ident = Søknadsfelt("fødselsnummer", "1234578901"),
                statsborgerskap = Søknadsfelt("statsborgerskap", listOf("NOR")),
                adresse = Søknadsfelt("adresse", SøknadAdresse(
                    adressenavn = null,
                    postnummer = null,
                    husbokstav = null,
                    bruksenhetsnummer = null,
                    husnummer = null,
                    poststed = null
                )),
                sivilstand = Søknadsfelt("sivilstand", SIVILSTANDTYPE.GIFT),
                telefonnummer = Søknadsfelt("Telefon", "40123456"),
                spørsmål = mapOf(),
        )
    }

    fun barn(): List<Barn> {
        return listOf(
            Barn(
                navn = Søknadsfelt("Barnets fulle navn", "barn1"),
                ident = Søknadsfelt("Fødselsnummer", "12345678999"),
                borMedSøker = Søknadsfelt("Skal ha samme adresse", true),
                alder = Søknadsfelt("alder", "4 år"),
                spørsmål = mapOf(),
            ),
            Barn(
                navn = Søknadsfelt("Barnets fulle navn", "barn2"),
                ident = Søknadsfelt("Fødselsnummer", "12345678987"),
                borMedSøker = Søknadsfelt("Skal ha samme adresse", true),
                alder = Søknadsfelt("alder", "1 år"),
                spørsmål = mapOf(),
            )
        )
    }

    fun søknad(): Søknad {
        return Søknad(
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
                    )
                )
            )
        )
    }
}