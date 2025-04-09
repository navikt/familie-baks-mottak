@file:Suppress("ktlint:standard:filename")

package no.nav.familie.baks.mottak.søknad.barnetrygd.mapper

import no.nav.familie.baks.mottak.domene.LabelVerdiPar
import no.nav.familie.kontrakter.ba.søknad.v8.Arbeidsperiode
import no.nav.familie.kontrakter.ba.søknad.v8.Pensjonsperiode
import no.nav.familie.kontrakter.ba.søknad.v8.Søker
import no.nav.familie.kontrakter.felles.søknad.Søknadsfelt

data class LivssituasjonenDinSeksjon(
    val erAsylsøker: LabelVerdiPar<String>,
    val arbeidIUtlandet: LabelVerdiPar<String>,
    val mottarUtenlandspensjon: LabelVerdiPar<String>,
    val arbeidsperioderIUtlandet: LabelVerdiPar<Arbeidsperioder>,
    val pensjonsperioderIUtlandet: LabelVerdiPar<Pensjonsperioder>,
)

fun mapTilLivssituasjonenDinSeksjon(
    søker: Søker,
    teksterUtenomSpørsmål: Map<String, Map<String, String>>,
    språk: String,
): LabelVerdiPar<LivssituasjonenDinSeksjon> {
    val arbeidsperiode = mapArbeidsperioderUtland(søker.arbeidsperioderUtland, språk)
    val pensjonsperiode = mapPensjonsperioderUtland(søker.pensjonsperioderUtland, språk)
    return LabelVerdiPar(
        label = hentTekst(teksterUtenomSpørsmål, "pdf.livssituasjonenDin.seksjonstittel", språk),
        verdi =
            LivssituasjonenDinSeksjon(
                erAsylsøker =
                    hentSpørsmålOversettOgFormatertilUtskrift<String>(
                        søker.spørsmål,
                        "erAsylsøker",
                        språk,
                    ),
                arbeidIUtlandet =
                    hentSpørsmålOversettOgFormatertilUtskrift<String>(
                        søker.spørsmål,
                        "arbeidIUtlandet",
                        språk,
                    ),
                mottarUtenlandspensjon =
                    hentSpørsmålOversettOgFormatertilUtskrift<String>(
                        søker.spørsmål,
                        "mottarUtenlandspensjon",
                        språk,
                    ),
                arbeidsperioderIUtlandet = LabelVerdiPar(label = "Arbeidsperioder i utlandet", verdi = Arbeidsperioder(arbeidsperiode)),
                pensjonsperioderIUtlandet =
                    LabelVerdiPar(
                        label = "Pensjonsperioder i utlandet",
                        verdi = Pensjonsperioder(pensjonsperiode),
                    ),
            ),
    )
}

data class Arbeidsperioder(
    val labelVerdiPar: List<LabelVerdiPar<no.nav.familie.baks.mottak.søknad.barnetrygd.mapper.Arbeidsperiode>>,
)

data class Arbeidsperiode(
    val arbeidsperiodeAvsluttet: LabelVerdiPar<String>,
    val arbeidsgiver: LabelVerdiPar<String>,
    val fraDatoArbeidsperiode: LabelVerdiPar<String>,
    val tilDatoArbeidsperiode: LabelVerdiPar<String>,
)

data class Pensjonsperioder(
    val labelVerdiPar: List<LabelVerdiPar<Pensjonperiode>>,
)

data class Pensjonperiode(
    val mottarPensjonNå: LabelVerdiPar<String>,
    val pensjonsland: LabelVerdiPar<String>,
    val pensjonFra: LabelVerdiPar<String>,
    val pensjonTil: LabelVerdiPar<String>,
)

// Kan gjøre de her til en generisk funksjon??

fun mapArbeidsperioderUtland(
    arbeidsperioderUtland: List<Søknadsfelt<Arbeidsperiode>>,
    språk: String,
): List<LabelVerdiPar<no.nav.familie.baks.mottak.søknad.barnetrygd.mapper.Arbeidsperiode>> =
    arbeidsperioderUtland.map { arbeidsperiode ->
        val label = arbeidsperiode.label
        val arbeidsperiodeAvsluttet = arbeidsperiode.verdi[språk]?.arbeidsperiodeAvsluttet
        val arbeidsgiver = arbeidsperiode.verdi[språk]?.arbeidsgiver
        val fraDatoArbeidsperiode = arbeidsperiode.verdi[språk]?.fraDatoArbeidsperiode
        val tilDatoArbeidsperiode = arbeidsperiode.verdi[språk]?.tilDatoArbeidsperiode
        LabelVerdiPar(
            label = label[språk] ?: "",
            verdi =
                Arbeidsperiode(
                    arbeidsperiodeAvsluttet = oversettOgFormaterTilUtskriftsformat(arbeidsperiodeAvsluttet, språk),
                    arbeidsgiver = oversettOgFormaterTilUtskriftsformat(arbeidsgiver, språk),
                    fraDatoArbeidsperiode = oversettOgFormaterTilUtskriftsformat(fraDatoArbeidsperiode, språk),
                    tilDatoArbeidsperiode = oversettOgFormaterTilUtskriftsformat(tilDatoArbeidsperiode, språk),
                ),
        )
    }

fun mapPensjonsperioderUtland(
    arbeidsperioderUtland: List<Søknadsfelt<Pensjonsperiode>>,
    språk: String,
): List<LabelVerdiPar<Pensjonperiode>> =
    arbeidsperioderUtland.map { arbeidsperiode ->
        val label = arbeidsperiode.label
        val mottarPensjonNå = arbeidsperiode.verdi[språk]?.mottarPensjonNå
        val pensjonsland = arbeidsperiode.verdi[språk]?.pensjonsland
        val pensjonFra = arbeidsperiode.verdi[språk]?.pensjonFra
        val pensjonTil = arbeidsperiode.verdi[språk]?.pensjonTil
        LabelVerdiPar(
            label = label[språk] ?: "",
            verdi =
                Pensjonperiode(
                    mottarPensjonNå = oversettOgFormaterTilUtskriftsformat(mottarPensjonNå, språk),
                    pensjonsland = oversettOgFormaterTilUtskriftsformat(pensjonsland, språk),
                    pensjonFra = oversettOgFormaterTilUtskriftsformat(pensjonFra, språk),
                    pensjonTil = oversettOgFormaterTilUtskriftsformat(pensjonTil, språk),
                ),
        )
    }
