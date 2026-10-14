package no.nav.familie.baks.mottak.søknad.barnetrygd.domene

import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknad
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV10
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV7
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV8
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV9
import no.nav.familie.kontrakter.felles.søknad.BaFellesDokumentasjonsbehov

fun VersjonertBarnetrygdSøknad.harEøsSteg(): Boolean =
    when (this) {
        is VersjonertBarnetrygdSøknadV7 -> barnetrygdSøknad.søker.harEøsSteg
        is VersjonertBarnetrygdSøknadV8 -> barnetrygdSøknad.søker.harEøsSteg
        is VersjonertBarnetrygdSøknadV9 -> barnetrygdSøknad.søker.harEøsSteg
        is VersjonertBarnetrygdSøknadV10 -> barnetrygdSøknad.søker.harEøsSteg
    }

fun VersjonertBarnetrygdSøknad.harDokumentasjonsbehovUtenVedlegg(): Boolean =
    barnetrygdSøknad.dokumentasjon
        .filter { it.dokumentasjonsbehov.tilFellesDokumentasjonsbehov() != BaFellesDokumentasjonsbehov.AnnenDokumentasjon }
        .any { it.opplastedeVedlegg.isEmpty() }
