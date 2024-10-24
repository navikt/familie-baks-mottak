package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadService
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV8
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV9
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV4
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV5
import org.springframework.stereotype.Service

@Service
class SøknadsidenterService(
    private val barnetrygdSøknadService: BarnetrygdSøknadService,
    private val kontantstøtteSøknadService: KontantstøtteSøknadService,
) {
    fun hentIdenterForKontantstøtteViaJournalpost(journalpostId: String): Pair<String, List<String>> {
        val versjonertSøknad =
            kontantstøtteSøknadService
                .hentDBKontantstøtteSøknadForJournalpost(journalpostId)
                .hentVersjonertKontantstøtteSøknad()
        return when (versjonertSøknad) {
            is VersjonertKontantstøtteSøknadV4 ->
                Pair(
                    versjonertSøknad.kontantstøtteSøknad.søker.ident.verdi.values
                        .first(),
                    versjonertSøknad.kontantstøtteSøknad.barn.map {
                        it.ident.verdi.values
                            .first()
                    },
                )

            is VersjonertKontantstøtteSøknadV5 ->
                Pair(
                    versjonertSøknad.kontantstøtteSøknad.søker.ident.verdi.values
                        .first(),
                    versjonertSøknad.kontantstøtteSøknad.barn.map {
                        it.ident.verdi.values
                            .first()
                    },
                )
        }
    }

    fun hentIdenterForBarnetrygdViaJournalpost(journalpostId: String): Pair<String, List<String>> {
        val versjonertSøknad =
            barnetrygdSøknadService
                .hentDBSøknadFraJournalpost(journalpostId)
                .hentVersjonertBarnetrygdSøknad()
        return when (versjonertSøknad) {
            is VersjonertBarnetrygdSøknadV8 ->
                Pair(
                    versjonertSøknad.barnetrygdSøknad.søker.ident.verdi.values
                        .first(),
                    versjonertSøknad.barnetrygdSøknad.barn.map {
                        it.ident.verdi.values
                            .first()
                    },
                )

            is VersjonertBarnetrygdSøknadV9 ->
                Pair(
                    versjonertSøknad.barnetrygdSøknad.søker.ident.verdi.values
                        .first(),
                    versjonertSøknad.barnetrygdSøknad.barn.map {
                        it.ident.verdi.values
                            .first()
                    },
                )
        }
    }
}
