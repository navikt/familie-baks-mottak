package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.journalføring.AdressebeskyttelesesgraderingService
import no.nav.familie.baks.mottak.journalføring.JournalpostBrukerService
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class EnhetsnummerService(
    private val hentEnhetClient: HentEnhetClient,
    private val arbeidsfordelingClient: ArbeidsfordelingClient,
    private val adressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService,
    private val journalpostBrukerService: JournalpostBrukerService,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

    fun hentEnhetsnummer(
        journalpost: Journalpost,
    ): String? {
        val journalpostTema = journalpost.tema

        if (journalpostTema == null) {
            logger.error("Journalpost tema er null for journalpost ${journalpost.journalpostId}.")
            throw IllegalStateException("Tema er null")
        }
        
        val journalpostBruker = journalpost.bruker ?: return null

        val tema = Tema.valueOf(journalpostTema)

        val erEnAvPersoneneStrengtFortrolig =
            adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = tema,
                journalpost = journalpost,
            )

        val journalførendeEnhet = journalpost.journalforendeEnhet

        return when {
            erEnAvPersoneneStrengtFortrolig -> "2103"
            journalførendeEnhet == "2101" -> "4806" // Enhet 2101 er nedlagt. Rutes til 4806
            journalførendeEnhet == "4847" -> "4817" // Enhet 4847 skal legges ned. Rutes til 4817
            journalpost.harDigitalSøknad(tema) -> arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(journalpostBrukerService.tilPersonIdent(journalpostBruker, tema), tema).enhetId
            journalførendeEnhet.isNullOrBlank() -> null
            hentEnhetClient.hentEnhet(journalførendeEnhet).status.uppercase(Locale.getDefault()) == "NEDLAGT" -> null
            hentEnhetClient.hentEnhet(journalførendeEnhet).oppgavebehandler -> journalførendeEnhet
            else -> {
                logger.warn("Enhet $journalførendeEnhet kan ikke ta i mot oppgaver")
                null
            }
        }
    }
}
