package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV9
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV4
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV5
import no.nav.familie.kontrakter.felles.Tema
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class EnhetsnummerService(
    private val hentEnhetClient: HentEnhetClient,
    private val pdlClient: PdlClient,
    private val barnetrygdSøknadService: BarnetrygdSøknadService,
    private val kontantstøtteSøknadService: KontantstøtteSøknadService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun hentEnhetsnummer(
        journalpost: Journalpost,
    ): String? {
        if (journalpost.tema == null) {
            throw IllegalStateException("Tema er null")
        }

        if (journalpost.bruker == null) {
            throw IllegalStateException("Bruker er null")
        }

        val tema = Tema.valueOf(journalpost.tema)

        val identer = when (tema) {
            Tema.BAR -> hentIdenterForBarnetrygd(journalpost)
            Tema.KON -> hentIdenterForKontantstøtte(journalpost)
            Tema.ENF,
            Tema.OPP -> {
                throw IllegalStateException("Støtter ikke tema $tema")
            }
        }

        val erStrengtFortrolig =
            identer
                // TODO : Kan vi forbedre graphql-spørringen?
                .map { pdlClient.hentPerson(it, "hentperson-med-adressebeskyttelse", tema) }
                .flatMap { it.adressebeskyttelse }
                .any { it.gradering.erStrengtFortrolig() }

        return when {
            erStrengtFortrolig -> "2103"
            journalpost.journalforendeEnhet == "2101" -> "4806" // Enhet 2101 er nedlagt. Rutes til 4806
            journalpost.journalforendeEnhet == "4847" -> "4817" // Enhet 4847 skal legges ned. Rutes til 4817
            journalpost.journalforendeEnhet.isNullOrBlank() -> null
            hentEnhetClient.hentEnhet(journalpost.journalforendeEnhet).status.uppercase(Locale.getDefault()) == "NEDLAGT" -> null
            hentEnhetClient.hentEnhet(journalpost.journalforendeEnhet).oppgavebehandler -> journalpost.journalforendeEnhet
            else -> {
                logger.warn("Enhet ${journalpost.journalforendeEnhet} kan ikke ta i mot oppgaver")
                null
            }
        }
    }

    private fun hentIdenterForKontantstøtte(journalpost: Journalpost): List<String> {
        val søknad = kontantstøtteSøknadService.hentDBKontantstøtteSøknadForJournalpost(journalpost.journalpostId)
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

    private fun hentIdenterForBarnetrygd(journalpost: Journalpost): List<String> {
        val søknad = barnetrygdSøknadService.hentDBSøknadFraJournalpost(journalpost.journalpostId)
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
