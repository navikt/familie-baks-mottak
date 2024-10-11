package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.domene.personopplysning.Adressebeskyttelsesgradering
import no.nav.familie.baks.mottak.integrasjoner.Bruker
import no.nav.familie.baks.mottak.integrasjoner.Journalpost
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.SøknadsidenterService
import no.nav.familie.baks.mottak.integrasjoner.erDigitalSøknad
import no.nav.familie.kontrakter.felles.Tema
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
    ): Boolean = finnAdressebeskyttelsegraderingerPåJournalpost(tema, journalpost).any { it.erStrengtFortrolig() || it.erStrengtFortroligUtland() }

    private fun finnAdressebeskyttelsegraderingerPåJournalpost(
        tema: Tema,
        journalpost: Journalpost,
    ): List<Adressebeskyttelsesgradering> {
        if (journalpost.bruker == null) {
            throw IllegalStateException("Bruker på journalpost ${journalpost.journalpostId} kan ikke være null")
        }

        val (søkersIdent, barnasIdenter) =
            when (tema) {
                Tema.BAR -> finnIdenterForBarnetrygd(tema, journalpost.bruker, journalpost.journalpostId, journalpost.erDigitalSøknad())
                Tema.KON -> finnIdenterForKontantstøtte(tema, journalpost.bruker, journalpost.journalpostId, journalpost.erDigitalSøknad())
                Tema.ENF,
                Tema.OPP,
                -> {
                    throw IllegalStateException("Støtter ikke tema $tema")
                }
            }

        val alleIdenter = barnasIdenter + søkersIdent

        return alleIdenter
            .map { pdlClient.hentPerson(it, "hentperson-med-adressebeskyttelse", tema) }
            .flatMap { it.adressebeskyttelse }
            .map { it.gradering }
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
                journalpostBrukerService.tilPersonIdent(
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
                journalpostBrukerService.tilPersonIdent(
                    bruker,
                    tema,
                ),
                emptyList(),
            )
        }
}
