package no.nav.familie.baks.mottak.søknad.kontantstøtte

import no.nav.familie.kontrakter.felles.søknad.Søknadsfelt
import no.nav.familie.kontrakter.ks.søknad.v1.RegistrertBostedType
import no.nav.familie.kontrakter.ks.søknad.v1.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ks.søknad.v4.Barn
import no.nav.familie.kontrakter.ks.søknad.v4.Søker
import no.nav.familie.kontrakter.ks.søknad.v6.KontantstøtteSøknad

fun <T> søknadsfelt(
    label: String,
    verdi: T,
): Søknadsfelt<T> = Søknadsfelt(label = mapOf("nb" to label), verdi = mapOf("nb" to verdi))

object KontantstøtteSøknadTestData {
    fun kontantstøtteSøknad(
        søker: Søker = lagSøker(),
        barn: List<Barn> = listOf(lagBarn()),
    ): KontantstøtteSøknad =
        KontantstøtteSøknad(
            kontraktVersjon = 5,
            antallEøsSteg = 2,
            søker = søker,
            barn = barn,
            dokumentasjon = emptyList(),
            teksterTilPdf = emptyMap(),
            originalSpråk = "nb",
            erNoenAvBarnaFosterbarn = søknadsfelt("Noen fosterbarn", "NEI"),
            søktAsylForBarn = søknadsfelt("Søkt asyl for barn", "NEI"),
            oppholderBarnSegIInstitusjon = søknadsfelt("Barn i institusjon", "NEI"),
            barnOppholdtSegTolvMndSammenhengendeINorge = søknadsfelt("Sammenhengende i Norge i 12 mnd", "JA"),
            erBarnAdoptert = søknadsfelt("Er barn adoptert", "NEI"),
            mottarKontantstøtteForBarnFraAnnetEøsland = søknadsfelt("Kontantstøtte annet land", "NEI"),
            harEllerTildeltBarnehageplass = søknadsfelt("Har barnehageplass", "NEI"),
            erAvdødPartnerForelder = null,
            finnesPersonMedAdressebeskyttelse = false,
        )

    fun lagSøker(
        fnr: String = "12345678910",
    ): Søker =
        Søker(
            harEøsSteg = false,
            ident = søknadsfelt("Fødselsnummer", fnr),
            navn = søknadsfelt("Navn", "Ola Norman"),
            statsborgerskap = søknadsfelt("Statsborgerskap", listOf("Norge")),
            adresse = søknadsfelt("Adresse", null),
            adressebeskyttelse = false,
            sivilstand = søknadsfelt("Sivilstand", SIVILSTANDTYPE.SEPARERT),
            borPåRegistrertAdresse = søknadsfelt("Bor på registrert adresse", "JA"),
            værtINorgeITolvMåneder = søknadsfelt("Norge 12 mnd", "JA"),
            utenlandsoppholdUtenArbeid = søknadsfelt("Opphold i utlandet uten arbeid", "JA"),
            utenlandsperioder = emptyList(),
            planleggerÅBoINorgeTolvMnd = søknadsfelt("Planlegger å bo i Norge i 12 mnd", "JA"),
            yrkesaktivFemÅr = søknadsfelt("Yrkesaktiv 5 år", "JA"),
            erAsylsøker = søknadsfelt("Er asylsøker", "NEI"),
            arbeidIUtlandet = søknadsfelt("Arbeid i utlandet", "NEI"),
            mottarUtenlandspensjon = søknadsfelt("Mottar pensjon fra utlandet", "NEI"),
            arbeidsperioderUtland = emptyList(),
            pensjonsperioderUtland = emptyList(),
            arbeidINorge = søknadsfelt("Arbeid i Norge", "JA"),
            arbeidsperioderNorge = emptyList(),
            pensjonNorge = søknadsfelt("Pensjon i Norge", "NEI"),
            pensjonsperioderNorge = emptyList(),
            andreUtbetalingsperioder = emptyList(),
            idNummer = emptyList(),
            andreUtbetalinger = søknadsfelt("Andre utbetalinger", "NEI"),
            adresseISøkeperiode = søknadsfelt("Adresse i søknadsperiode", "Testgate 123"),
        )

    fun lagBarn(
        fnr: String = "12345678999",
    ): Barn =
        Barn(
            harEøsSteg = true,
            navn = søknadsfelt("Barnets fulle navn", "barn1"),
            ident = søknadsfelt("Fødselsnummer", fnr),
            registrertBostedType = søknadsfelt("Skal ha samme adresse", RegistrertBostedType.REGISTRERT_ANNEN_ADRESSE),
            alder = søknadsfelt("alder", "4 år"),
            utenlandsperioder = emptyList(),
            teksterTilPdf = emptyMap(),
            erFosterbarn = søknadsfelt("erFosterbarn", "JA"),
            oppholderSegIInstitusjon = søknadsfelt("oppholderSegIInstitusjon", "JA"),
            adresse = søknadsfelt("adresse", "Galtvort 123"),
            andreForelder = null,
            andreForelderErDød = null,
            boddMindreEnn12MndINorge = søknadsfelt("boddMindreEnn12MndINorge", "JA"),
            borFastMedSøker = søknadsfelt("borFastMedSøker", "JA"),
            borMedAndreForelder = null,
            borMedOmsorgsperson = null,
            erAdoptert = søknadsfelt("erAdoptert", "NEI"),
            erAsylsøker = søknadsfelt("erAsylsøker", "NEI"),
            foreldreBorSammen = null,
            harBarnehageplass = søknadsfelt("harBarnehageplass", "NEI"),
            kontantstøtteFraAnnetEøsland = søknadsfelt("kontantstøtteFraAnnetEøsland", "NEI"),
            mottarEllerMottokEøsKontantstøtte = null,
            omsorgsperson = null,
            planleggerÅBoINorge12Mnd = null,
            pågåendeSøknadHvilketLand = null,
            pågåendeSøknadFraAnnetEøsLand = null,
            søkerDeltKontantstøtte = null,
            søkersSlektsforhold = null,
            søkersSlektsforholdSpesifisering = null,
            utbetaltForeldrepengerEllerEngangsstønad = null,
        )
}
