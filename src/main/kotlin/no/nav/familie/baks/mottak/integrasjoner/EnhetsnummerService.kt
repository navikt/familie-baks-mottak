package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.Tema
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class EnhetsnummerService(
    private val hentEnhetClient: HentEnhetClient,
    private val pdlClient: PdlClient,
    private val søknadFraJournalpostService: SøknadFraJournalpostService,
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

        val identer =
            when (tema) {
                Tema.BAR -> søknadFraJournalpostService.hentIdenterForBarnetrygd(journalpost.journalpostId)
                Tema.KON -> søknadFraJournalpostService.hentIdenterForKontantstøtte(journalpost.journalpostId)
                Tema.ENF,
                Tema.OPP,
                -> {
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
}
