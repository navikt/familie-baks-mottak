package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV9
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV4
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV5
import org.springframework.stereotype.Service

@Service
class SøknadFraJournalpostService(
    private val barnetrygdSøknadService: BarnetrygdSøknadService,
    private val kontantstøtteSøknadService: KontantstøtteSøknadService
) {

    fun hentIdenterForKontantstøtte(journalpostId: String): List<String> {
        val søknad = kontantstøtteSøknadService.hentDBKontantstøtteSøknadForJournalpost(journalpostId)
        val versjonertSøknad = søknad?.hentVersjonertKontantstøtteSøknad()
        return when (versjonertSøknad) {
            is KontantstøtteSøknadV4 -> {
                val identSøker = versjonertSøknad.kontantstøtteSøknad.søker.ident.verdi.values.first()
                val identBarn = versjonertSøknad.kontantstøtteSøknad.barn.map { it.ident.verdi.values.first() }
                identBarn + identSøker
            }

            is KontantstøtteSøknadV5 -> {
                val identSøker = versjonertSøknad.kontantstøtteSøknad.søker.ident.verdi.values.first()
                val identBarn = versjonertSøknad.kontantstøtteSøknad.barn.map { it.ident.verdi.values.first() }
                identBarn + identSøker
            }

            null -> throw IllegalStateException("Støtter ikke versjonert søknad $versjonertSøknad")
        }
    }

    fun hentIdenterForBarnetrygd(journalpostId: String): List<String> {
        val søknad = barnetrygdSøknadService.hentDBSøknadFraJournalpost(journalpostId)
        val versjonertSøknad = søknad?.hentVersjonertSøknad()
        return when (versjonertSøknad) {
            is BarnetrygdSøknadV8 -> {
                val identSøker = versjonertSøknad.barnetrygdSøknad.søker.ident.verdi.values.first()
                val identBarn = versjonertSøknad.barnetrygdSøknad.barn.map { it.ident.verdi.values.first() }
                identBarn + identSøker
            }

            is BarnetrygdSøknadV9 -> {
                val identSøker = versjonertSøknad.barnetrygdSøknad.søker.ident.verdi.values.first()
                val identBarn = versjonertSøknad.barnetrygdSøknad.barn.map { it.ident.verdi.values.first() }
                identBarn + identSøker
            }

            null -> throw IllegalStateException("Støtter ikke versjonert søknad $versjonertSøknad")
        }
    }

}