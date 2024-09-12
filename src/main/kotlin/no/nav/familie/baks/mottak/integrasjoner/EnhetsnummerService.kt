package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.journalføring.AdressebeskyttelesesgraderingService
import no.nav.familie.baks.mottak.journalføring.JournalpostBrukerService
import no.nav.familie.kontrakter.felles.Tema
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
        if (journalpost.tema == null) {
            logger.error("Journalpost tema er null for journalpost ${journalpost.journalpostId}.")
            throw IllegalStateException("Tema er null")
        }

        if (journalpost.bruker == null) {
            logger.error("Bruker for journalpost ${journalpost.journalpostId} er null. Usikker på hvordan dette burde håndteres. Se SecureLogs.")
            secureLogger.error("Bruker for journalpost $journalpost er null. Usikker på hvordan dette burde håndteres.")
            throw IllegalStateException("Bruker for journalpost ${journalpost.journalpostId} er null. Usikker på hvordan dette burde håndteres.")
        }

        val tema = Tema.valueOf(journalpost.tema)

        val erEnAvPersoneneStrengtFortrolig =
            adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = tema,
                journalpost = journalpost,
            )

        return when {
            erEnAvPersoneneStrengtFortrolig -> "2103"
            journalpost.journalforendeEnhet == "2101" -> "4806" // Enhet 2101 er nedlagt. Rutes til 4806
            journalpost.journalforendeEnhet == "4847" -> "4817" // Enhet 4847 skal legges ned. Rutes til 4817
            journalpost.erDigitalSøknad() -> arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(journalpostBrukerService.tilPersonIdent(journalpost.bruker, tema), tema).enhetId
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
