package no.nav.familie.ba.mottak.søknad.domene

import no.nav.familie.kontrakter.ba.søknad.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ba.søknad.SøknadAdresse
import no.nav.familie.kontrakter.ba.søknad.v4.Locale
import no.nav.familie.kontrakter.ba.søknad.v4.NåværendeSamboer
import no.nav.familie.kontrakter.ba.søknad.v4.SpørsmålId
import no.nav.familie.kontrakter.ba.søknad.v4.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsfelt
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.v4.TidligereSamboer
import no.nav.familie.kontrakter.ba.søknad.v4.Utenlandsopphold
import no.nav.familie.kontrakter.ba.søknad.v6.Barn

/**
 * WIP v7 av Søknad.
 * All bruk av denne er togglet av i prod.
 * Når typen er komplett vil den bli flyttet til familie-kontrakter
 */
data class SøknadNewWip(
    val kontraktVersjon: Int,
    val søknadstype: Søknadstype,
    val søker: Søker,
    val barn: List<Barn>,
    val spørsmål: Map<SpørsmålId, Søknadsfelt<Any>>,
    val dokumentasjon: List<Søknaddokumentasjon>,
    val teksterUtenomSpørsmål: Map<SpørsmålId, Map<Locale, String>>,
    val originalSpråk: Locale
)

data class Søker(
    val ident: Søknadsfelt<String>,
    val navn: Søknadsfelt<String>,
    val statsborgerskap: Søknadsfelt<List<String>>,
    val adresse: Søknadsfelt<SøknadAdresse>,
    val sivilstand: Søknadsfelt<SIVILSTANDTYPE>,
    val spørsmål: Map<String, Søknadsfelt<Any>>,
    val nåværendeSamboer: Søknadsfelt<NåværendeSamboer>?,
    val tidligereSamboere: List<Søknadsfelt<TidligereSamboer>>,
    val utenlandsperioder: List<Søknadsfelt<Utenlandsopphold>> = listOf(),
    val andreUtbetalingsperioder: List<Søknadsfelt<Utbetalingsperiode>> = listOf(),
    val arbeidsperioderUtland: List<Søknadsfelt<Arbeidsperiode>> = listOf(),
    val arbeidsperioderNorge: List<Søknadsfelt<Arbeidsperiode>> = listOf(),
    val pensjonsperioderNorge: List<Søknadsfelt<Pensjonsperiode>> = listOf(),
    val pensjonsperioderUtland: List<Søknadsfelt<Pensjonsperiode>> = listOf()
)

data class Arbeidsperiode(
    val arbeidsperiodeAvsluttet: Søknadsfelt<String?>,
    val arbeidsperiodeland: Søknadsfelt<String?>,
    val arbeidsgiver: Søknadsfelt<String?>,
    val fraDatoArbeidsperiode: Søknadsfelt<String?>,
    val tilDatoArbeidsperiode: Søknadsfelt<String?>,
)

data class Pensjonsperiode(
    val mottarPensjonNå: Søknadsfelt<String?>,
    val pensjonsland: Søknadsfelt<String?>,
    val pensjonFra: Søknadsfelt<String?>,
    val pensjonTil: Søknadsfelt<String?>,
)

data class Utbetalingsperiode(
    val fårUtbetalingNå: Søknadsfelt<String?>,
    val utbetalingLand: Søknadsfelt<String?>,
    val utbetalingFraDato: Søknadsfelt<String?>,
    val utbetalingTilDato: Søknadsfelt<String?>,
)
