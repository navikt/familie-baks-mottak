package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV9
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV4
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV5
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SøknadsidenterService(
    private val barnetrygdSøknadService: BarnetrygdSøknadService,
    private val kontantstøtteSøknadService: KontantstøtteSøknadService,
) {
    private val logger: Logger = LoggerFactory.getLogger(SøknadsidenterService::class.java)

    fun hentIdenterForKontantstøtteViaJournalpost(journalpostId: String): Pair<String, List<String>> {
        val søknad = kontantstøtteSøknadService.hentDBKontantstøtteSøknadForJournalpost(journalpostId)
        if (søknad == null) {
            logger.error("Fant ikke søknad for journalpost=$journalpostId")
            throw IllegalStateException("Fant ikke søknad for journalpost=$journalpostId")
        }
        val versjonertSøknad = søknad.hentVersjonertKontantstøtteSøknad()
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
        val søknad = barnetrygdSøknadService.hentDBSøknadFraJournalpost(journalpostId)
        if (søknad == null) {
            logger.error("Fant ikke søknad for journalpost=$journalpostId")
            throw IllegalStateException("Fant ikke søknad for journalpost=$journalpostId")
        }
        val versjonertSøknad = søknad.hentVersjonertSøknad()
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
