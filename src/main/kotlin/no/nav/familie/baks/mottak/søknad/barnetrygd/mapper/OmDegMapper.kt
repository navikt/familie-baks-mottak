@file:Suppress("ktlint:standard:filename")

package no.nav.familie.baks.mottak.søknad.barnetrygd.mapper

import no.nav.familie.baks.mottak.domene.LabelVerdiPar
import no.nav.familie.kontrakter.ba.søknad.v8.Søker

data class OmDegSeksjon(
    val ident: LabelVerdiPar<String>,
    val statsborgerskap: LabelVerdiPar<String>,
    val sivilstatus: LabelVerdiPar<String>,
    val adresse: LabelVerdiPar<String>,
)

fun mapTilOmDegSeksjon(
    søker: Søker,
    språk: String,
): OmDegSeksjon =
    OmDegSeksjon(
        ident = oversettOgFormaterTilUtskriftsformat(søker.ident, språk),
        statsborgerskap = oversettOgFormaterTilUtskriftsformat(søker.statsborgerskap, språk),
        sivilstatus = oversettOgFormaterTilUtskriftsformat(søker.sivilstand, språk),
        adresse = oversettOgFormaterTilUtskriftsformat(søker.adresse, språk),
    )
