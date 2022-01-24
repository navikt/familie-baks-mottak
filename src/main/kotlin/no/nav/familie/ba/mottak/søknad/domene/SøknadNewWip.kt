package no.nav.familie.ba.mottak.søknad.domene

import no.nav.familie.kontrakter.ba.søknad.v4.Locale
import no.nav.familie.kontrakter.ba.søknad.v4.SpørsmålId
import no.nav.familie.kontrakter.ba.søknad.v4.Søker
import no.nav.familie.kontrakter.ba.søknad.v4.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsfelt
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.v6.Barn

/**
 * WIP v7 av Søknad.
 * All bruk av denne er togglet av i prod.
 * Når typen er komplett vil den bli flyttet til familie-kontrakter
 */
data class SøknadNewWip(
    val versjon: Int,
    val søknadstype: Søknadstype,
    val søker: Søker,
    val barn: List<Barn>,
    val spørsmål: Map<SpørsmålId, Søknadsfelt<Any>>,
    val dokumentasjon: List<Søknaddokumentasjon>,
    val teksterUtenomSpørsmål: Map<SpørsmålId, Map<Locale, String>>,
    val originalSpråk: Locale
)
