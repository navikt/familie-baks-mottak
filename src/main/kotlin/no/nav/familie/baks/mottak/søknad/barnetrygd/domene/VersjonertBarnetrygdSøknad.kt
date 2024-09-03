package no.nav.familie.baks.mottak.søknad.barnetrygd.domene

import no.nav.familie.kontrakter.ba.søknad.v8.Søknad as BarnetrygdSøknadV8
import no.nav.familie.kontrakter.ba.søknad.v9.BarnetrygdSøknad as BarnetrygdSøknadV9

sealed class VersjonertBarnetrygdSøknad

data class BarnetrygdSøknadV8(
    val barnetrygdSøknad: BarnetrygdSøknadV8,
) : VersjonertBarnetrygdSøknad()

data class BarnetrygdSøknadV9(
    val barnetrygdSøknad: BarnetrygdSøknadV9,
) : VersjonertBarnetrygdSøknad()
