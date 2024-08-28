package no.nav.familie.baks.mottak.søknad.kontantstøtte.domene

import no.nav.familie.kontrakter.ks.søknad.v4.KontantstøtteSøknad as KontantstøtteSøknadV4
import no.nav.familie.kontrakter.ks.søknad.v5.KontantstøtteSøknad as KontantstøtteSøknadV5

sealed class VersjonertKontantstøtteSøknad

data class KontantstøtteSøknadV4(
    val kontantstøtteSøknad: KontantstøtteSøknadV4,
) : VersjonertKontantstøtteSøknad()

data class KontantstøtteSøknadV5(
    val kontantstøtteSøknad: KontantstøtteSøknadV5,
) : VersjonertKontantstøtteSøknad()
