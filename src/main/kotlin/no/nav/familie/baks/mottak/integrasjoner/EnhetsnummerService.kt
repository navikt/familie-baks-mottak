package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class EnhetsnummerService(
    private val hentEnhetClient: HentEnhetClient,
    private val pdlClient: PdlClient
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun hentEnhetsnummer(
        journalpost: Journalpost,
    ): String? {

        if (journalpost.tema == null) {
            throw IllegalStateException("Tema finnes ikke")
        }

        if (journalpost.bruker == null) {
            throw IllegalStateException("Bruker finnes ikke")
        }

        val tema = Tema.valueOf(journalpost.tema)
        val brukersIdent = tilPersonIdent(journalpost.bruker, tema)

        val person = pdlClient.hentPersonMedRelasjoner(brukersIdent, tema)
        val barna = person
            .forelderBarnRelasjoner
            .filter {
                it.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.BARN ||
                // TODO : Kan et dødfødt barn ha kode 6/7/19?
                it.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.DOEDFOEDT_BARN
            }

        val personErStrengtFortrolig = person
            .adressebeskyttelseGradering
            .any { it.erStrengtFortrolig() }

        val minstEtBarnErStrengtFortrolig = barna
            .mapNotNull { it.relatertPersonsIdent }
            .map { pdlClient.hentPerson(it, "hentperson-barn", tema) }
            .flatMap { it.adressebeskyttelse }
            .any { it.gradering.erStrengtFortrolig()}

        return when {
            personErStrengtFortrolig -> "2103"
            minstEtBarnErStrengtFortrolig -> "2103"
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

    private fun tilPersonIdent(
        bruker: Bruker,
        tema: Tema,
    ): String =
        when (bruker.type) {
            BrukerIdType.AKTOERID -> pdlClient.hentPersonident(bruker.id, tema)
            else -> bruker.id
        }

}