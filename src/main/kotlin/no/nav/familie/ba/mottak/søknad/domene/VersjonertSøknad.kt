package no.nav.familie.baks.mottak.søknad.domene

import no.nav.familie.kontrakter.ba.søknad.v7.Søknad as SøknadV7
import no.nav.familie.kontrakter.ba.søknad.v8.Søknad as SøknadV8

sealed class VersjonertSøknad

data class SøknadV7(val søknad: SøknadV7) : VersjonertSøknad()
data class SøknadV8(val søknad: SøknadV8) : VersjonertSøknad()
