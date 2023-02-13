package no.nav.familie.baks.mottak.søknad.kontantstøtte.domene

import no.nav.familie.kontrakter.ks.søknad.v1.KontantstøtteSøknad as SøknadV1
import no.nav.familie.kontrakter.ks.søknad.v2.KontantstøtteSøknad as SøknadV2

sealed class VersjonertKontantstøtteSøknad

data class KontantstøtteSøknadV1(val søknad: SøknadV1) : VersjonertKontantstøtteSøknad()
data class KontantstøtteSøknadV2(val søknad: SøknadV2) : VersjonertKontantstøtteSøknad()
