package no.nav.familie.ba.mottak.søknad

import no.nav.familie.kontrakter.ba.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.Barn
import no.nav.familie.kontrakter.ba.søknad.Søker
import no.nav.familie.kontrakter.ba.søknad.Søknad
import no.nav.familie.kontrakter.ba.søknad.Søknadsfelt

object SøknadTestData {
    fun søker(): Søker {
        return Søker(
                Søknadsfelt("navn", "Navn Navnessen"),
                Søknadsfelt("fødselsnummer", "1234578901")
        )
    }

    fun barn(): List<Søknadsfelt<Barn>> {
        return listOf(
                Søknadsfelt("barn", Barn(
                        navn = Søknadsfelt("Barnets fulle navn", "barn1"),
                        ident = Søknadsfelt("Fødselsnummer", "12345678999"),
                        borMedSøker = Søknadsfelt("Skal ha samme adresse", "bor med søker"),
                        medISøknad = Søknadsfelt("Jeg søker for dette barnet", true),
                        alder = Søknadsfelt("alder", "4 år")
                )),
                Søknadsfelt("barn", Barn(
                        navn = Søknadsfelt("Barnets fulle navn", "barn2"),
                        ident = Søknadsfelt("Fødselsnummer", "12345678987"),
                        borMedSøker = Søknadsfelt("Skal ha samme adresse", "bor med søker"),
                        medISøknad = Søknadsfelt("Jeg søker for dette barnet", false),
                        alder = Søknadsfelt("alder", "1 år")
                ))
        )
    }

    fun søknad(): Søknad {
        return Søknad(
                søker = Søknadsfelt("søker", søker()),
                barn = Søknadsfelt("barna dine", barn()),
                søknadstype = Søknadsfelt("Søknadstype", Søknadstype.ORDINÆR)
        )
    }
}