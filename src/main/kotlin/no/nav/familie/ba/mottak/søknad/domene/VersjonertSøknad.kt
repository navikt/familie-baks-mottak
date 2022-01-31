package no.nav.familie.ba.mottak.søknad.domene

import no.nav.familie.kontrakter.ba.søknad.v6.Søknad

sealed class VersjonertSøknad

data class SøknadV6(val søknad: Søknad) : VersjonertSøknad()
data class SøknadV7(val søknad: SøknadNewWip) : VersjonertSøknad()

