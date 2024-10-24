package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV9
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadService
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
                .hentVersjonertSøknad()
        return when (versjonertSøknad) {
            is BarnetrygdSøknadV8 ->
                Pair(
                    versjonertSøknad.barnetrygdSøknad.søker.ident.verdi.values
                        .first(),
                    versjonertSøknad.barnetrygdSøknad.barn.map {
                        it.ident.verdi.values
                            .first()
                    },
                )

            is BarnetrygdSøknadV9 ->
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
