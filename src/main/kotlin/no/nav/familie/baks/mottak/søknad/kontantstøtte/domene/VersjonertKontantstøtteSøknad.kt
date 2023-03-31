package no.nav.familie.baks.mottak.søknad.kontantstøtte.domene

import no.nav.familie.kontrakter.ks.søknad.v3.KontantstøtteSøknad as KontantstøtteSøknadV3
import no.nav.familie.kontrakter.ks.søknad.v4.KontantstøtteSøknad as KontantstøtteSøknadV4

sealed class VersjonertKontantstøtteSøknad

data class KontantstøtteSøknadV3(val kontantstøtteSøknad: KontantstøtteSøknadV3) :
    VersjonertKontantstøtteSøknad()

data class KontantstøtteSøknadV4(val kontantstøtteSøknad: KontantstøtteSøknadV4) :
    VersjonertKontantstøtteSøknad()
