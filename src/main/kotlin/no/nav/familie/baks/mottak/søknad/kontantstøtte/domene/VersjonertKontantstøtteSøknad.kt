package no.nav.familie.baks.mottak.søknad.kontantstøtte.domene

import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknad
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV1
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV2
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV3
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV4
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV5
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV6

fun VersjonertKontantstøtteSøknad.harEøsSteg(): Boolean =
    when (this) {
        is VersjonertKontantstøtteSøknadV1 -> kontantstøtteSøknad.søker.harEøsSteg
        is VersjonertKontantstøtteSøknadV2 -> kontantstøtteSøknad.søker.harEøsSteg
        is VersjonertKontantstøtteSøknadV3 -> kontantstøtteSøknad.søker.harEøsSteg
        is VersjonertKontantstøtteSøknadV4 -> kontantstøtteSøknad.søker.harEøsSteg
        is VersjonertKontantstøtteSøknadV5 -> kontantstøtteSøknad.søker.harEøsSteg
        is VersjonertKontantstøtteSøknadV6 -> kontantstøtteSøknad.søker.harEøsSteg
    }
