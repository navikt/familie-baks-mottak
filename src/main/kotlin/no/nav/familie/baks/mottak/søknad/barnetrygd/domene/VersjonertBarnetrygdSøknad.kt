package no.nav.familie.baks.mottak.søknad.barnetrygd.domene

import no.nav.familie.kontrakter.ba.søknad.v8.Søknad as SøknadV8

sealed class VersjonertBarnetrygdSøknad

data class SøknadV8(val søknad: SøknadV8) : VersjonertBarnetrygdSøknad()
