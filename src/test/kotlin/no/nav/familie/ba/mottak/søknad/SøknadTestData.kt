package no.nav.familie.ba.mottak.søknad

import com.fasterxml.jackson.core.type.TypeReference
import io.mockk.mockk
import no.nav.familie.kontrakter.ba.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.Barn
import no.nav.familie.kontrakter.ba.søknad.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ba.søknad.v2.Søker as SøkerV2
import no.nav.familie.kontrakter.ba.søknad.v3.Søker as SøkerV3
import no.nav.familie.kontrakter.ba.søknad.Søknad
import no.nav.familie.kontrakter.ba.søknad.v2.Søknad as SøknadV2
import no.nav.familie.kontrakter.ba.søknad.v3.Søknad as SøknadV3
import no.nav.familie.kontrakter.ba.søknad.SøknadAdresse
import no.nav.familie.kontrakter.ba.søknad.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.Søknadsfelt
import no.nav.familie.kontrakter.ba.søknad.Søknadsvedlegg
import no.nav.familie.kontrakter.felles.objectMapper

object SøknadTestData {
    fun søker(): SøkerV2 {
        return SøkerV2(
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

    fun søknad(): SøknadV2 {
        return SøknadV2(
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

    fun søknadV1(): Søknad {
        val map = objectMapper.convertValue(søknad(), object:TypeReference<MutableMap<String, Any>>(){})
        val søker: MutableMap<String, Any> = map.get("søker") as MutableMap<String, Any>
        søker.put("telefonnummer", Søknadsfelt("Telefonnummer", "40123456"))

        return objectMapper.convertValue(map, Søknad::class.java)
    }

    fun tomv3søknad(): SøknadV3 {
        return SøknadV3(
            søknadstype = Søknadstype.UTVIDET,
            barn = listOf(),
            dokumentasjon = listOf(),
            spørsmål = mapOf(),
            søker = mockk()
        )
    }
}