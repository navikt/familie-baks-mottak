package no.nav.familie.baks.mottak.søknad.kontantstøtte.domene

import no.nav.familie.kontrakter.ks.søknad.v2.KontantstøtteSøknad as SøknadV2
import no.nav.familie.kontrakter.ks.søknad.v3.KontantstøtteSøknad as SøknadV3

sealed class VersjonertKontantstøtteSøknad

data class KontantstøtteSøknadV2(val søknad: SøknadV2) : VersjonertKontantstøtteSøknad()

data class KontantstøtteSøknadV3(val søknad: SøknadV3) : VersjonertKontantstøtteSøknad()
