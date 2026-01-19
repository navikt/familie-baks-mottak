package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleService
import no.nav.familie.baks.mottak.integrasjoner.ArbeidsfordelingClient
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.finnesÅpenBehandlingPåFagsak
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import org.springframework.stereotype.Service

@Service
class AutomatiskJournalføringKontantstøtteService(
    private val featureToggleService: FeatureToggleService,
    private val arbeidsfordelingClient: ArbeidsfordelingClient,
    private val ksSakClient: KsSakClient,
    private val adressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService,
    private val journalpostBrukerService: JournalpostBrukerService,
) {
    private val tema = Tema.KON
    private val enheterSomIkkeSkalHaAutomatiskJournalføring = listOf("4863")

    fun skalAutomatiskJournalføres(
        journalpost: Journalpost,
    ): Boolean {
        if (!journalpost.harKontantstøtteSøknad()) {
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

        val fagsakId = ksSakClient.hentFagsaknummerPåPersonident(personIdent)
        val minimalFagsak = ksSakClient.hentMinimalRestFagsak(fagsakId)

        if (minimalFagsak.finnesÅpenBehandlingPåFagsak()) {
            return false
        }

        return true
    }
}
