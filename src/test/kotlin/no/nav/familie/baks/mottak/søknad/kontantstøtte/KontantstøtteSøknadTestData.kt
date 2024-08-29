package no.nav.familie.baks.mottak.søknad.kontantstøtte

import no.nav.familie.kontrakter.ks.søknad.v1.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ks.søknad.v1.Søknadsfelt
import no.nav.familie.kontrakter.ks.søknad.v4.KontantstøtteSøknad
import no.nav.familie.kontrakter.ks.søknad.v4.Søker

fun <T> søknadsfelt(
    label: String,
    verdi: T,
): Søknadsfelt<T> = Søknadsfelt(label = mapOf("nb" to label), verdi = mapOf("nb" to verdi))

object KontantstøtteSøknadTestData {
    fun kontantstøtteSøknad(): KontantstøtteSøknad =
        KontantstøtteSøknad(
            kontraktVersjon = 5,
            antallEøsSteg = 2,
            søker = lagSøker(),
            barn = emptyList(),
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
        )

    private fun lagSøker(): Søker =
        Søker(
            harEøsSteg = false,
            ident = søknadsfelt("Fødselsnummer", "12345678910"),
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
}
