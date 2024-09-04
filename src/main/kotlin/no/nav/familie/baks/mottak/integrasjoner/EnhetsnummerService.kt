package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.Tema
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

    fun hentEnhetsnummer(
        journalpost: Journalpost,
    ): String? {
        if (journalpost.tema == null) {
            throw IllegalStateException("Tema er null")
        }

        if (journalpost.bruker == null) {
            throw IllegalStateException("Fant ikke bruker på journalpost ved forsøk på henting av behandlende enhet")
        }

        val erPapirsøknad = !journalpost.erDigitalKanal()
        val tema = Tema.valueOf(journalpost.tema)

        val (søkersIdent, barnasIdenter) =
            when (tema) {
                Tema.BAR -> finnIdenterForBarnetrygd(erPapirsøknad, tema, journalpost.bruker, journalpost.journalpostId)
                Tema.KON -> finnIdenterForKontantstøtte(erPapirsøknad, tema, journalpost.bruker, journalpost.journalpostId)
                Tema.ENF,
                Tema.OPP,
                -> {
                    throw IllegalStateException("Støtter ikke tema $tema")
                }
            }

        val alleIdenter = barnasIdenter + søkersIdent

        val erStrengtFortrolig =
            alleIdenter
                .map { pdlClient.hentPerson(it, "hentperson-med-adressebeskyttelse", tema) }
                .flatMap { it.adressebeskyttelse }
                .any { it.gradering.erStrengtFortrolig() }

        return when {
            erStrengtFortrolig -> "2103"
            journalpost.journalforendeEnhet == "2101" -> "4806" // Enhet 2101 er nedlagt. Rutes til 4806
            journalpost.journalforendeEnhet == "4847" -> "4817" // Enhet 4847 skal legges ned. Rutes til 4817
            journalpost.erDigitalKanal() && (journalpost.erBarnetrygdSøknad() || journalpost.erKontantstøtteSøknad()) ->
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
        erPapirsøknad: Boolean,
        tema: Tema,
        bruker: Bruker,
        journalpostId: String,
    ): Pair<String, List<String>> =
        if (erPapirsøknad) {
            Pair(
                tilPersonIdent(bruker, tema),
                emptyList(),
            )
        } else {
            søknadsidenterService.hentIdenterForKontantstøtteViaJournalpost(journalpostId)
        }

    private fun finnIdenterForBarnetrygd(
        erPapirsøknad: Boolean,
        tema: Tema,
        bruker: Bruker,
        journalpostId: String,
    ): Pair<String, List<String>> =
        if (erPapirsøknad) {
            Pair(
                tilPersonIdent(bruker, tema),
                emptyList(),
            )
        } else {
            søknadsidenterService.hentIdenterForBarnetrygdViaJournalpost(journalpostId)
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
