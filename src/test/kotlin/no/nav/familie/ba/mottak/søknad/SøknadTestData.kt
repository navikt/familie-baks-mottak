package no.nav.familie.ba.mottak.søknad

import com.fasterxml.jackson.core.type.TypeReference
import io.mockk.mockk
import no.nav.familie.kontrakter.ba.Søknadstype as SøknadstypeGammel
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.v4.Barn
import no.nav.familie.kontrakter.ba.søknad.v4.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ba.søknad.v4.Søker as SøkerV4
import no.nav.familie.kontrakter.ba.søknad.Søknad
import no.nav.familie.kontrakter.ba.søknad.v3.Søknad as SøknadV3
import no.nav.familie.kontrakter.ba.søknad.v4.Søknad as SøknadV4
import no.nav.familie.kontrakter.ba.søknad.SøknadAdresse
import no.nav.familie.kontrakter.ba.søknad.v4.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.Søknadsfelt
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsfelt as SøknadsfeltV4
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsvedlegg
import no.nav.familie.kontrakter.ba.søknad.v3.UtvidetSøkerInfo
import no.nav.familie.kontrakter.felles.objectMapper

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

    fun barn(): List<Barn> {
        return listOf(
            Barn(
                navn = søknadsfelt("Barnets fulle navn", "barn1"),
                ident = søknadsfelt("Fødselsnummer", "12345678999"),
                borMedSøker = søknadsfelt("Skal ha samme adresse", true),
                alder = søknadsfelt("alder", "4 år"),
                spørsmål = mapOf(),
            ),
            Barn(
                navn = søknadsfelt("Barnets fulle navn", "barn2"),
                ident = søknadsfelt("Fødselsnummer", "12345678987"),
                borMedSøker = søknadsfelt("Skal ha samme adresse", true),
                alder = søknadsfelt("alder", "1 år"),
                spørsmål = mapOf(),
            )
        )
    }

    fun søknad(): SøknadV4 {
        return SøknadV4(
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

    fun søknadV1(): Søknad {
        val map = objectMapper.convertValue(søknad(), object:TypeReference<MutableMap<String, Any>>(){})
        val søker: MutableMap<String, Any> = map["søker"] as MutableMap<String, Any>
        søker["telefonnummer"] = Søknadsfelt("Telefonnummer", "40123456")

        return objectMapper.convertValue(map, Søknad::class.java)
    }

    fun tomv3søknad(): SøknadV3 {
        return SøknadV3(
            søknadstype = SøknadstypeGammel.UTVIDET,
            barn = listOf(),
            dokumentasjon = listOf(),
            spørsmål = mapOf(),
            søker = no.nav.familie.kontrakter.ba.søknad.v3.Søker(
                ident = Søknadsfelt("ident", "123123123"),
                adresse = Søknadsfelt("adresse", mockk()),
                navn = Søknadsfelt("navn", "Hahaha"),
                sivilstand = Søknadsfelt("stand", SIVILSTANDTYPE.ENKE_ELLER_ENKEMANN),
                statsborgerskap = Søknadsfelt("sttsb", listOf()),
                utvidet = Søknadsfelt("Utvidet", UtvidetSøkerInfo(
                    spørsmål = mapOf(),
                    nåværendeSamboer = null,
                    tidligereSamboere = listOf()
                )),
                spørsmål = mapOf()
            )
        )
    }
}