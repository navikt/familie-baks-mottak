@file:Suppress("ktlint:standard:filename")

package no.nav.familie.baks.mottak.søknad.barnetrygd.mapper

import no.nav.familie.baks.mottak.domene.LabelVerdiPar
import no.nav.familie.kontrakter.ba.søknad.v9.BarnetrygdSøknad

data class StrukturertBarnetrygdSøknad(
    val omDeg: LabelVerdiPar<OmDegSeksjon>,
    val livssituasjonenDin: LabelVerdiPar<LivssituasjonenDinSeksjon>,
    val hvilkeBarnSeksjon: LabelVerdiPar<List<HvilkeBarnSeksjon>>,
)

fun mapTilBarnetrygd(
    søknad: BarnetrygdSøknad,
    språk: String,
): StrukturertBarnetrygdSøknad =
    StrukturertBarnetrygdSøknad(
        omDeg =
            mapTilOmDegSeksjon(
                søknad.søker,
                søknad.teksterUtenomSpørsmål,
                språk,
            ),
        livssituasjonenDin = mapTilLivssituasjonenDinSeksjon(søknad.søker, søknad.teksterUtenomSpørsmål, språk),
        hvilkeBarnSeksjon = mapTilHvilkeBarnSeksjon(søknad.barn, søknad.teksterUtenomSpørsmål, språk),
    )
