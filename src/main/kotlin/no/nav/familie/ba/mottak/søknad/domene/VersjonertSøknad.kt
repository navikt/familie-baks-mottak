package no.nav.familie.ba.mottak.søknad.domene

import no.nav.familie.kontrakter.ba.søknad.v7.Søknad as SøknadV7


sealed class VersjonertSøknad

data class SøknadV7(val søknad: SøknadV7) : VersjonertSøknad()
data class SøknadV8(val søknad: SøknadWipV8) : VersjonertSøknad()

