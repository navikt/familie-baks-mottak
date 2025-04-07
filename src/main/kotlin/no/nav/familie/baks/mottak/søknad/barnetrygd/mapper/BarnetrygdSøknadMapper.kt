@file:Suppress("ktlint:standard:filename")

package no.nav.familie.baks.mottak.søknad.barnetrygd.mapper

import no.nav.familie.kontrakter.ba.søknad.v9.BarnetrygdSøknad

data class StrukturertBarnetrygdSøknad(
    val omDeg: OmDegSeksjon? = null,
)

fun mapTilBarnetrygd(
    søknad: BarnetrygdSøknad,
    språk: String,
): StrukturertBarnetrygdSøknad =
    StrukturertBarnetrygdSøknad(
        omDeg = mapTilOmDegSeksjon(søknad.søker, språk),
    )
