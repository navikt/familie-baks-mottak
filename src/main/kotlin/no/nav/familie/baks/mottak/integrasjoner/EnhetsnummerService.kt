package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.Tema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class EnhetsnummerService(
    private val hentEnhetClient: HentEnhetClient,
    private val pdlClient: PdlClient,
    private val søknadsidenterService: SøknadsidenterService,
    private val arbeidsfordelingClient: ArbeidsfordelingClient,
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
            logger.error("Bruker for journalpost er null for journalpost ${journalpost.journalpostId}. Se SecureLogs.")
            secureLogger.error("Bruker for journalpost er null for journalpost $journalpost.")
            throw IllegalStateException("Journalpost bruker er null")
        }

        val erDigitalSøknad = journalpost.erDigitalKanal() && (journalpost.erKontantstøtteSøknad() || journalpost.erBarnetrygdSøknad())
        val tema = Tema.valueOf(journalpost.tema)

        val (søkersIdent, barnasIdenter) =
            when (tema) {
                Tema.BAR -> finnIdenterForBarnetrygd(tema, journalpost.bruker, journalpost.journalpostId, erDigitalSøknad)
                Tema.KON -> finnIdenterForKontantstøtte(tema, journalpost.bruker, journalpost.journalpostId, erDigitalSøknad)
                Tema.ENF,
                Tema.OPP,
                -> {
                    throw IllegalStateException("Støtter ikke tema $tema")
                }
            }

        val alleIdenter = barnasIdenter + søkersIdent

        val erEnAvPersoneneStrengtFortrolig =
            alleIdenter
                .map { pdlClient.hentPerson(it, "hentperson-med-adressebeskyttelse", tema) }
                .flatMap { it.adressebeskyttelse }
                .any { it.gradering.erStrengtFortrolig() }

        return when {
            erEnAvPersoneneStrengtFortrolig -> "2103"
            journalpost.journalforendeEnhet == "2101" -> "4806" // Enhet 2101 er nedlagt. Rutes til 4806
            journalpost.journalforendeEnhet == "4847" -> "4817" // Enhet 4847 skal legges ned. Rutes til 4817
            erDigitalSøknad ->
                arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(søkersIdent, tema).enhetId
            journalpost.journalforendeEnhet.isNullOrBlank() -> null
            hentEnhetClient.hentEnhet(journalpost.journalforendeEnhet).status.uppercase(Locale.getDefault()) == "NEDLAGT" -> null
            hentEnhetClient.hentEnhet(journalpost.journalforendeEnhet).oppgavebehandler -> journalpost.journalforendeEnhet
            else -> {
                logger.warn("Enhet ${journalpost.journalforendeEnhet} kan ikke ta i mot oppgaver")
                null
            }
        }
    }

    private fun finnIdenterForKontantstøtte(
        tema: Tema,
        bruker: Bruker,
        journalpostId: String,
        erDigitalSøknad: Boolean,
    ): Pair<String, List<String>> =
        if (erDigitalSøknad) {
            søknadsidenterService.hentIdenterForKontantstøtteViaJournalpost(journalpostId)
        } else {
            Pair(
                tilPersonIdent(
                    bruker,
                    tema,
                ),
                emptyList(),
            )
        }

    private fun finnIdenterForBarnetrygd(
        tema: Tema,
        bruker: Bruker,
        journalpostId: String,
        erDigitalSøknad: Boolean,
    ): Pair<String, List<String>> =
        if (erDigitalSøknad) {
            søknadsidenterService.hentIdenterForBarnetrygdViaJournalpost(journalpostId)
        } else {
            Pair(
                tilPersonIdent(
                    bruker,
                    tema,
                ),
                emptyList(),
            )
        }

    private fun tilPersonIdent(
        bruker: Bruker,
        tema: Tema,
    ): String =
        when (bruker.type) {
            BrukerIdType.AKTOERID -> pdlClient.hentPersonident(bruker.id, tema)
            else -> bruker.id
        }
}
