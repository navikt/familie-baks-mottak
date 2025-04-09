package no.nav.familie.baks.mottak.søknad.barnetrygd.mapper

import no.nav.familie.baks.mottak.domene.LabelVerdiPar
import no.nav.familie.kontrakter.ba.søknad.v1.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ba.søknad.v1.SøknadAdresse
import no.nav.familie.kontrakter.felles.søknad.Søknadsfelt

fun oversettOgFormaterTilUtskriftsformat(
    søknadsfelt: Søknadsfelt<*>?,
    språk: String,
): LabelVerdiPar<String> {
    val label = søknadsfelt?.label?.get(språk) ?: ""
    val verdi = mapVerdiToString(søknadsfelt?.verdi?.get(språk))
    return LabelVerdiPar(label, verdi)
}

fun mapVerdiToString(verdi: Any?): String =
    when (verdi) {
        is String -> verdi
        is SøknadAdresse -> adresseUtskriftsformat(verdi)
        is List<*> -> listeUtskriftsformat(verdi as List<String>) // TODO Antar at lister har strings. Må kanskje ta en titt på dette
        is SIVILSTANDTYPE -> verdi.toString() // TODO hjelpefunksjon som gjør enumen brukervennlig
        else -> ""
    }

fun adresseUtskriftsformat(adresse: SøknadAdresse): String = "${adresse.adressenavn ?: ""} ${adresse.husnummer ?: ""}${adresse.husbokstav ?: ""} ${adresse.bruksenhetsnummer ?: ""} \n ${adresse.postnummer ?: ""} ${adresse.poststed ?: ""}"

fun listeUtskriftsformat(listeMedStenger: List<String>): String = listeMedStenger.joinToString(separator = "\n")

fun mapVerdiTilLabelVerdiPar(
    søknadsfelt: Søknadsfelt<*>?,
    språk: String,
): LabelVerdiPar<String> {
    val label = søknadsfelt?.label?.get(språk) ?: ""
    val verdi = mapVerdiToString(søknadsfelt?.verdi?.get(språk))
    return LabelVerdiPar(label, verdi)
}

fun <T> getSøknadsfelt(
    data: Map<String, Søknadsfelt<Any>>,
    key: String,
): Søknadsfelt<T>? {
    @Suppress("UNCHECKED_CAST")
    return data[key] as? Søknadsfelt<T>
}

fun <T> hentSpørsmålOversettOgFormatertilUtskrift(
    spørsmål: Map<String, Søknadsfelt<Any>>,
    nøkkel: String,
    språk: String,
): LabelVerdiPar<String> {
    val felt =
        getSøknadsfelt<T>(spørsmål, nøkkel)
            ?: Søknadsfelt(label = mapOf(språk to nøkkel), verdi = mapOf(språk to ""))
    return oversettOgFormaterTilUtskriftsformat(felt, språk)
}

fun hentTekst(
    tekster: Map<String, Map<String, String>>,
    nøkkel: String,
    locale: String,
    default: String = "",
): String = tekster[nøkkel]?.get(locale) ?: default
