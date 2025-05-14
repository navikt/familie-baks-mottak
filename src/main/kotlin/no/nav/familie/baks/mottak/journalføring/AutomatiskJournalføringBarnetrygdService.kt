package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.integrasjoner.ArbeidsfordelingClient
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.finnesÅpenBehandlingPåFagsak
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.unleash.UnleashService
import org.springframework.stereotype.Service

@Service
class AutomatiskJournalføringBarnetrygdService(
    private val unleashService: UnleashService,
    private val baSakClient: BaSakClient,
    private val arbeidsfordelingClient: ArbeidsfordelingClient,
    private val adressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService,
    private val journalpostBrukerService: JournalpostBrukerService,
) {
    private val tema = Tema.BAR
    private val enheterSomIkkeSkalHaAutomatiskJournalføring = listOf("4863", "2103")

    fun skalAutomatiskJournalføres(
        journalpost: Journalpost,
        brukerHarSakIInfotrygd: Boolean,
    ): Boolean {
        if (!journalpost.harBarnetrygdSøknad()) {
            return false
        }

        if (brukerHarSakIInfotrygd) {
            return false
        }

        if (!journalpost.erDigitalKanal()) {
            return false
        }

        if (adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)) {
            return false
        }

        val bruker = journalpost.bruker!!

        if (bruker.type == BrukerIdType.ORGNR) {
            return false
        }

        val personIdent = journalpostBrukerService.tilPersonIdent(bruker, tema)
        val enhetId = arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(personIdent, tema).enhetId

        if (enhetId in enheterSomIkkeSkalHaAutomatiskJournalføring) {
            return false
        }

        val fagsakId = baSakClient.hentFagsaknummerPåPersonident(personIdent)
        val minimalFagsak = baSakClient.hentMinimalRestFagsak(fagsakId)

        if (minimalFagsak.finnesÅpenBehandlingPåFagsak()) {
            return false
        }

        return true
    }
}
