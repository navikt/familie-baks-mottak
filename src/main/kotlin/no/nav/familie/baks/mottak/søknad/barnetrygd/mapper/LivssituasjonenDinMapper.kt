@file:Suppress("ktlint:standard:filename")

package no.nav.familie.baks.mottak.søknad.barnetrygd.mapper

import no.nav.familie.baks.mottak.domene.LabelVerdiPar
import no.nav.familie.kontrakter.ba.søknad.v8.Arbeidsperiode
import no.nav.familie.kontrakter.ba.søknad.v8.Søker
import no.nav.familie.kontrakter.felles.søknad.Søknadsfelt

data class LivssituasjonenDinSeksjon(
    val erAsylsøker: LabelVerdiPar<String>,
    val arbeidIUtlandet: LabelVerdiPar<String>,
    val mottarUtenlandspensjon: LabelVerdiPar<String>,
    val arbeidsperioderIUtlandet: LabelVerdiPar<Arbeidsperioder1>,
)

fun mapTilLivssituasjonenDinSeksjon(
    søker: Søker,
    språk: String,
): LabelVerdiPar<LivssituasjonenDinSeksjon> {
    val arbeidsperiode = mapArbeidsperioderUtland(søker.arbeidsperioderUtland, språk)
    println("KOKOKOKOKOK" + arbeidsperiode)
    return LabelVerdiPar(
        label = "Livssituasjonen din",
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
                arbeidsperioderIUtlandet = LabelVerdiPar(label = "Arbeidsperioder i utlandet", verdi = Arbeidsperioder1(arbeidsperiode)),
            ),
    )
}

data class Arbeidsperioder1(
    val labelVerdiPar: List<LabelVerdiPar<Arbeidsperiode1>>,
)

data class Arbeidsperiode1(
    val arbeidsperiodeAvsluttet: LabelVerdiPar<String>,
    val arbeidsgiver: LabelVerdiPar<String>,
    val fraDatoArbeidsperiode: LabelVerdiPar<String>,
    val tilDatoArbeidsperiode: LabelVerdiPar<String>,
)

fun mapArbeidsperioderUtland(
    arbeidsperioderUtland: List<Søknadsfelt<Arbeidsperiode>>,
    språk: String,
): List<LabelVerdiPar<Arbeidsperiode1>> =
    arbeidsperioderUtland.map { arbeidsperiode ->
        val label = arbeidsperiode.label
        val arbeidsperiodeAvsluttet = arbeidsperiode.verdi[språk]?.arbeidsperiodeAvsluttet
        val arbeidsgiver = arbeidsperiode.verdi[språk]?.arbeidsgiver
        val fraDatoArbeidsperiode = arbeidsperiode.verdi[språk]?.fraDatoArbeidsperiode
        val tilDatoArbeidsperiode = arbeidsperiode.verdi[språk]?.tilDatoArbeidsperiode
        LabelVerdiPar(
            label = label[språk] ?: "",
            verdi =
                Arbeidsperiode1(
                    arbeidsperiodeAvsluttet = mapVerdiTilLabelVerdiPar(arbeidsperiodeAvsluttet, språk),
                    arbeidsgiver = mapVerdiTilLabelVerdiPar(arbeidsgiver, språk),
                    fraDatoArbeidsperiode = mapVerdiTilLabelVerdiPar(fraDatoArbeidsperiode, språk),
                    tilDatoArbeidsperiode = mapVerdiTilLabelVerdiPar(tilDatoArbeidsperiode, språk),
                ),
        )
    }
