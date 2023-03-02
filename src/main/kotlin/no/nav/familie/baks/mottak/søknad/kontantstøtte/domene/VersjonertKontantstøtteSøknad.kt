package no.nav.familie.baks.mottak.søknad.kontantstøtte.domene

import no.nav.familie.kontrakter.ks.søknad.v2.KontantstøtteSøknad as KontantstøtteSøknadV2
import no.nav.familie.kontrakter.ks.søknad.v3.KontantstøtteSøknad as KontantstøtteSøknadV3

sealed class VersjonertKontantstøtteSøknad

data class KontantstøtteSøknadV2(val kontantstøtteSøknad: KontantstøtteSøknadV2) : VersjonertKontantstøtteSøknad()

data class KontantstøtteSøknadV3(val kontantstøtteSøknad: KontantstøtteSøknadV3) : VersjonertKontantstøtteSøknad()
