package no.nav.familie.baks.mottak.søknad.barnetrygd.mapper

import no.nav.familie.baks.mottak.domene.LabelVerdiPar
import no.nav.familie.kontrakter.ba.søknad.v1.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ba.søknad.v1.SøknadAdresse
import no.nav.familie.kontrakter.felles.søknad.Søknadsfelt

fun oversettOgFormaterTilUtskriftsformat(
    søknadsfelt: Søknadsfelt<*>,
    språk: String,
): LabelVerdiPar<String> {
    val label = søknadsfelt.label[språk] ?: ""

    val verdi =
        when (val v = søknadsfelt.verdi[språk]) {
            is String -> v
            is SøknadAdresse -> adresseUtskriftsformat(v)
            is List<*> -> listeUtskriftsformat(v as List<String>) // TODO Antar at lister har strings. Må kanskje ta en titt på dette
            is SIVILSTANDTYPE -> v.toString() // TODO hjelpefunksjon som gjør enumen brukervennlig
            else -> ""
        }

    return LabelVerdiPar(label, verdi)
}

fun adresseUtskriftsformat(adresse: SøknadAdresse): String = "${adresse.adressenavn ?: ""} ${adresse.husnummer ?: ""}${adresse.husbokstav ?: ""} ${adresse.bruksenhetsnummer ?: ""} \n ${adresse.postnummer ?: ""} ${adresse.poststed ?: ""}"

fun listeUtskriftsformat(listeMedStenger: List<String>): String = listeMedStenger.joinToString(separator = "\n")
