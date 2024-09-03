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
class SøknadFraJournalpostService(
    private val barnetrygdSøknadService: BarnetrygdSøknadService,
    private val kontantstøtteSøknadService: KontantstøtteSøknadService,
) {
    private val logger: Logger = LoggerFactory.getLogger(SøknadFraJournalpostService::class.java)

    fun hentBarnasIdenterForKontantstøtte(journalpostId: String): List<String> {
        val søknad = kontantstøtteSøknadService.hentDBKontantstøtteSøknadForJournalpost(journalpostId)
        if (søknad == null) {
            logger.error("Fant ikke søknad for journalpost=$journalpostId")
            throw IllegalStateException("Fant ikke søknad for journalpost=$journalpostId")
        }
        val versjonertSøknad = søknad.hentVersjonertKontantstøtteSøknad()
        return when (versjonertSøknad) {
            is KontantstøtteSøknadV4 ->
                versjonertSøknad.kontantstøtteSøknad.barn.map {
                    it.ident.verdi.values
                        .first()
                }
            is KontantstøtteSøknadV5 ->
                versjonertSøknad.kontantstøtteSøknad.barn.map {
                    it.ident.verdi.values
                        .first()
                }
        }
    }

    fun hentSøkersIdentForKontantstøtte(journalpostId: String): String {
        val søknad = kontantstøtteSøknadService.hentDBKontantstøtteSøknadForJournalpost(journalpostId)
        if (søknad == null) {
            logger.error("Fant ikke søknad for journalpost=$journalpostId")
            throw IllegalStateException("Fant ikke søknad for journalpost=$journalpostId")
        }
        val versjonertSøknad = søknad.hentVersjonertKontantstøtteSøknad()
        return when (versjonertSøknad) {
            is KontantstøtteSøknadV4 ->
                versjonertSøknad.kontantstøtteSøknad.søker.ident.verdi.values
                    .first()
            is KontantstøtteSøknadV5 ->
                versjonertSøknad.kontantstøtteSøknad.søker.ident.verdi.values
                    .first()
        }
    }

    fun hentBarnasIdenterForBarnetrygd(journalpostId: String): List<String> {
        val søknad = barnetrygdSøknadService.hentDBSøknadFraJournalpost(journalpostId)
        if (søknad == null) {
            logger.error("Fant ikke søknad for journalpost=$journalpostId")
            throw IllegalStateException("Fant ikke søknad for journalpost=$journalpostId")
        }
        val versjonertSøknad = søknad.hentVersjonertSøknad()
        return when (versjonertSøknad) {
            is BarnetrygdSøknadV8 ->
                versjonertSøknad.barnetrygdSøknad.barn.map {
                    it.ident.verdi.values
                        .first()
                }
            is BarnetrygdSøknadV9 ->
                versjonertSøknad.barnetrygdSøknad.barn.map {
                    it.ident.verdi.values
                        .first()
                }
        }
    }

    fun hentSøkersIdentForBarnetrygd(journalpostId: String): String {
        val søknad = barnetrygdSøknadService.hentDBSøknadFraJournalpost(journalpostId)
        if (søknad == null) {
            logger.error("Fant ikke søknad for journalpost=$journalpostId")
            throw IllegalStateException("Fant ikke søknad for journalpost=$journalpostId")
        }
        val versjonertSøknad = søknad.hentVersjonertSøknad()
        return when (versjonertSøknad) {
            is BarnetrygdSøknadV8 ->
                versjonertSøknad.barnetrygdSøknad.søker.ident.verdi.values
                    .first()
            is BarnetrygdSøknadV9 ->
                versjonertSøknad.barnetrygdSøknad.søker.ident.verdi.values
                    .first()
        }
    }
}
