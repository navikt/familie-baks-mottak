package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.SøknadsidenterService
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import org.springframework.stereotype.Service

@Service
class AdressebeskyttelesesgraderingService(
    private val pdlClient: PdlClient,
    private val søknadsidenterService: SøknadsidenterService,
    private val journalpostBrukerService: JournalpostBrukerService,
) {
    fun finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
        tema: Tema,
        journalpost: Journalpost,
    ): Boolean {
        val journalpostBruker = journalpost.bruker ?: throw IllegalStateException("Bruker på journalpost ${journalpost.journalpostId} kan ikke være null")

        val alleIdenter =
            when (tema) {
                Tema.BAR -> finnIdenterForBarnetrygd(tema, journalpostBruker, journalpost.journalpostId, journalpost.harDigitalSøknad(tema))
                Tema.KON -> finnIdenterForKontantstøtte(tema, journalpostBruker, journalpost.journalpostId, journalpost.harDigitalSøknad(tema))
                Tema.ENF,
                Tema.OPP,
                -> {
                    throw IllegalStateException("Støtter ikke tema $tema")
                }
            }

        return alleIdenter
            .map { pdlClient.hentPerson(it, "hentperson-med-adressebeskyttelse", tema) }
            .flatMap { it.adressebeskyttelse }
            .any { it.gradering.erStrengtFortrolig() }
    }

    fun finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpostBruker(
        tema: Tema,
        journalpost: Journalpost,
    ): Boolean {
        val journalpostBruker = journalpost.bruker ?: throw IllegalStateException("Bruker på journalpost ${journalpost.journalpostId} kan ikke være null")
        val journalpostBrukerIdent = journalpostBrukerService.tilPersonIdent(journalpostBruker, tema)

        return pdlClient.hentPerson(journalpostBrukerIdent, "hentperson-med-adressebeskyttelse", tema).adressebeskyttelse.any { it.gradering.erStrengtFortrolig() }
    }

    private fun finnIdenterForKontantstøtte(
        tema: Tema,
        bruker: Bruker,
        journalpostId: String,
        erDigitalSøknad: Boolean,
    ): List<String> =
        if (erDigitalSøknad) {
            søknadsidenterService.hentIdenterFraKontantstøtteSøknad(journalpostId)
        } else {
            listOf(
                journalpostBrukerService.tilPersonIdent(
                    bruker,
                    tema,
                ),
            )
        }

    private fun finnIdenterForBarnetrygd(
        tema: Tema,
        bruker: Bruker,
        journalpostId: String,
        erDigitalSøknad: Boolean,
    ): List<String> =
        if (erDigitalSøknad) {
            søknadsidenterService.hentIdenterFraBarnetrygdSøknad(journalpostId)
        } else {
            listOf(
                journalpostBrukerService.tilPersonIdent(
                    bruker,
                    tema,
                ),
            )
        }
}
