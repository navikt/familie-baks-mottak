package no.nav.familie.baks.mottak.søknad.barnetrygd.domene

import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknad
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV10
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV7
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV8
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV9

fun VersjonertBarnetrygdSøknad.harEøsSteg(): Boolean =
    when (this) {
        is VersjonertBarnetrygdSøknadV7 -> barnetrygdSøknad.søker.harEøsSteg
        is VersjonertBarnetrygdSøknadV8 -> barnetrygdSøknad.søker.harEøsSteg
        is VersjonertBarnetrygdSøknadV9 -> barnetrygdSøknad.søker.harEøsSteg
        is VersjonertBarnetrygdSøknadV10 -> barnetrygdSøknad.søker.harEøsSteg
    }
