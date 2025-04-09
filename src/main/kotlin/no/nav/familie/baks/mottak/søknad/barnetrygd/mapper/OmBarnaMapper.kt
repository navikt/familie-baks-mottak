@file:Suppress("ktlint:standard:filename")

package no.nav.familie.baks.mottak.søknad.barnetrygd.mapper

import no.nav.familie.baks.mottak.domene.LabelVerdiPar
import no.nav.familie.kontrakter.ba.søknad.v8.Barn

data class OmBarnaSeksjon(
    val navn: LabelVerdiPar<String>,
)

fun mapTilOmBarnaSeksjon(
    barna: List<Barn>,
    teksterUtenomSpørsmål: Map<String, Map<String, String>>,
    språk: String,
): LabelVerdiPar<String> =
    LabelVerdiPar(
        label = hentTekst(teksterUtenomSpørsmål, "pdf.hvilkebarn.seksjonstittel", språk),
        verdi = "",
    )
