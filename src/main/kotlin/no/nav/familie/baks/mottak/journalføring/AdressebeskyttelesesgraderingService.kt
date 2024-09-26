package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.integrasjoner.Adressebeskyttelsesgradering
import no.nav.familie.baks.mottak.integrasjoner.Bruker
import no.nav.familie.baks.mottak.integrasjoner.Journalpost
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.SøknadsidenterService
import no.nav.familie.baks.mottak.integrasjoner.erDigitalSøknad
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.ADRESSEBESKYTTELSEGRADERING
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
    ): Boolean = finnAdressebeskyttelsegraderingPåJournalpost(tema, journalpost).any { it.erStrengtFortrolig() || it.erStrengtFortroligUtland() }

    fun finnStrengesteAdressebeskyttelsegraderingPåJournalpost(
        tema: Tema,
        journalpost: Journalpost,
    ): ADRESSEBESKYTTELSEGRADERING? {
        val adressebeskyttelsesgraderingPåJournalpost = finnAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)
        return when {
            adressebeskyttelsesgraderingPåJournalpost.any { it.erStrengtFortroligUtland() } -> ADRESSEBESKYTTELSEGRADERING.STRENGT_FORTROLIG_UTLAND
            adressebeskyttelsesgraderingPåJournalpost.any { it.erStrengtFortrolig() } -> ADRESSEBESKYTTELSEGRADERING.STRENGT_FORTROLIG
            adressebeskyttelsesgraderingPåJournalpost.any { it.erFortrolig() } -> ADRESSEBESKYTTELSEGRADERING.FORTROLIG
            adressebeskyttelsesgraderingPåJournalpost.any { it.erUgradert() } -> ADRESSEBESKYTTELSEGRADERING.UGRADERT
            else -> null
        }
    }

    private fun finnAdressebeskyttelsegraderingPåJournalpost(
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
