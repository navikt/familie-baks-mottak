package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV9
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV4
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV5
import no.nav.familie.kontrakter.felles.Tema
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
            is KontantstøtteSøknadV4 ->
                Pair(
                    versjonertSøknad.kontantstøtteSøknad.søker.ident.verdi.values
                        .first(),
                    versjonertSøknad.kontantstøtteSøknad.barn.map {
                        it.ident.verdi.values
                            .first()
                    },
                )

            is KontantstøtteSøknadV5 ->
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

    fun hentIdenterIDigitalSøknadFraJournalpost(tema: Tema, journalpostId: String): List<String> {
        val (søker, barn) = when (tema) {
            Tema.BAR -> hentIdenterForBarnetrygdViaJournalpost(journalpostId = journalpostId)
            Tema.KON -> hentIdenterForKontantstøtteViaJournalpost(journalpostId = journalpostId)
            else -> throw Error("Kan ikke hente identer i digital søknad for tema $tema")
        }
        return listOf(søker).plus(barn)
    }


}
