package no.nav.familie.ba.mottak.søknad.domene

import no.nav.familie.kontrakter.ba.søknad.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ba.søknad.SøknadAdresse
import no.nav.familie.kontrakter.ba.søknad.v4.Locale
import no.nav.familie.kontrakter.ba.søknad.v4.NåværendeSamboer
import no.nav.familie.kontrakter.ba.søknad.v4.SpørsmålId
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsfelt
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.v4.TidligereSamboer
import no.nav.familie.kontrakter.ba.søknad.v4.Utenlandsopphold
import no.nav.familie.kontrakter.ba.søknad.v5.RegistrertBostedType
import no.nav.familie.kontrakter.ba.søknad.v6.AndreForelderUtvidet
import no.nav.familie.kontrakter.ba.søknad.v7.Arbeidsperiode
import no.nav.familie.kontrakter.ba.søknad.v7.EøsBarnetrygdsperiode
import no.nav.familie.kontrakter.ba.søknad.v7.IdNummer
import no.nav.familie.kontrakter.ba.søknad.v7.Pensjonsperiode
import no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.v7.Utbetalingsperiode

data class SøknadWipV8(
    val kontraktVersjon: Int,
    val antallEøsSteg: Int,
    val søknadstype: Søknadstype,
    val søker: Søker,
    val barn: List<Barn>,
    val spørsmål: Map<SpørsmålId, Søknadsfelt<Any>>,
    val dokumentasjon: List<Søknaddokumentasjon>,
    val teksterUtenomSpørsmål: Map<SpørsmålId, Map<Locale, String>>,
    val originalSpråk: Locale
)

data class Søker(
    val harEøsSteg: Boolean,
    val ident: Søknadsfelt<String>,
    val navn: Søknadsfelt<String>,
    val statsborgerskap: Søknadsfelt<List<String>>,
    val adresse: Søknadsfelt<SøknadAdresse?>,
    val adressebeskyttelse: Boolean,
    val sivilstand: Søknadsfelt<SIVILSTANDTYPE>,
    val spørsmål: Map<String, Søknadsfelt<Any>>,
    val nåværendeSamboer: Søknadsfelt<NåværendeSamboer>? = null,
    val tidligereSamboere: List<Søknadsfelt<TidligereSamboer>> = listOf(),
    val utenlandsperioder: List<Søknadsfelt<Utenlandsopphold>> = listOf(),
    val andreUtbetalingsperioder: List<Søknadsfelt<Utbetalingsperiode>> = listOf(),
    val arbeidsperioderUtland: List<Søknadsfelt<Arbeidsperiode>> = listOf(),
    val arbeidsperioderNorge: List<Søknadsfelt<Arbeidsperiode>> = listOf(),
    val pensjonsperioderNorge: List<Søknadsfelt<Pensjonsperiode>> = listOf(),
    val pensjonsperioderUtland: List<Søknadsfelt<Pensjonsperiode>> = listOf(),
    val idNummer: List<Søknadsfelt<IdNummer>> = listOf()
)

data class Barn(
    val harEøsSteg: Boolean,
    val ident: Søknadsfelt<String>,
    val navn: Søknadsfelt<String>,
    val registrertBostedType: Søknadsfelt<RegistrertBostedType>,
    val alder: Søknadsfelt<String>? = null,
    val spørsmål: Map<String, Søknadsfelt<Any>>,
    val utenlandsperioder: List<Søknadsfelt<Utenlandsopphold>> = listOf(),
    val andreForelder: AndreForelder? = null,
    val omsorgsperson: Omsorgsperson? = null,
    val eøsBarnetrygdsperioder: List<Søknadsfelt<EøsBarnetrygdsperiode>> = listOf(),
    val idNummer: List<Søknadsfelt<IdNummer>> = listOf()
)

data class Omsorgsperson(
    val navn: Søknadsfelt<String>,
    val slektsforhold: Søknadsfelt<String>,
    val slektsforholdSpesifisering: Søknadsfelt<String?>,
    val idNummer: Søknadsfelt<String>,
    val adresse: Søknadsfelt<String>,
    val arbeidUtland: Søknadsfelt<String?>,
    val arbeidsperioderUtland: List<Søknadsfelt<Arbeidsperiode>> = listOf(),
    val arbeidNorge: Søknadsfelt<String?>,
    val arbeidsperioderNorge: List<Søknadsfelt<Arbeidsperiode>> = listOf(),
    val pensjonUtland: Søknadsfelt<String?>,
    val pensjonsperioderUtland: List<Søknadsfelt<Pensjonsperiode>> = listOf(),
    val pensjonNorge: Søknadsfelt<String?>,
    val pensjonsperioderNorge: List<Søknadsfelt<Pensjonsperiode>> = listOf(),
    val andreUtbetalinger: Søknadsfelt<String?>,
    val andreUtbetalingsperioder: List<Søknadsfelt<Utbetalingsperiode>> = listOf(),
    val pågåendeSøknadFraAnnetEøsLand: Søknadsfelt<String?>,
    val pågåendeSøknadHvilketLand: Søknadsfelt<String?>,
    val barnetrygdFraEøs: Søknadsfelt<String?>,
    val eøsBarnetrygdsperioder: List<Søknadsfelt<EøsBarnetrygdsperiode>> = listOf(),
)

data class AndreForelder(
    val kanIkkeGiOpplysninger: Boolean,
    val navn: Søknadsfelt<String>,
    val fnr: Søknadsfelt<String>,
    val fødselsdato: Søknadsfelt<String>,
    val arbeidUtlandet: Søknadsfelt<String>,
    val pensjonUtland: Søknadsfelt<String>,
    val skriftligAvtaleOmDeltBosted: Søknadsfelt<String>,
    val utvidet: AndreForelderUtvidet,
    val pensjonNorge: Søknadsfelt<String?>,
    val arbeidNorge: Søknadsfelt<String?>,
    val andreUtbetalinger: Søknadsfelt<String?>,
    val arbeidsperioderUtland: List<Søknadsfelt<Arbeidsperiode>> = listOf(),
    val pensjonsperioderUtland: List<Søknadsfelt<Pensjonsperiode>> = listOf(),
    val arbeidsperioderNorge: List<Søknadsfelt<Arbeidsperiode>> = listOf(),
    val pensjonsperioderNorge: List<Søknadsfelt<Pensjonsperiode>> = listOf(),
    val andreUtbetalingsperioder: List<Søknadsfelt<Utbetalingsperiode>> = listOf(),
    val idNummer: List<Søknadsfelt<IdNummer>> = listOf(),
    val adresse: Søknadsfelt<String>,
    val pågåendeSøknadFraAnnetEøsLand: Søknadsfelt<String?>,
    val pågåendeSøknadHvilketLand: Søknadsfelt<String?>,
    val barnetrygdFraEøs: Søknadsfelt<String?>,
    val eøsBarnetrygdsperioder: List<Søknadsfelt<EøsBarnetrygdsperiode>> = listOf(),
)
