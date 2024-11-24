package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.ArbeidsfordelingClient
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.erKontantstøtteSøknad
import no.nav.familie.baks.mottak.integrasjoner.finnesÅpenBehandlingPåFagsak
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import org.springframework.stereotype.Service

@Service
class AutomatiskJournalføringKontantstøtteService(
    private val unleashService: UnleashNextMedContextService,
    private val arbeidsfordelingClient: ArbeidsfordelingClient,
    private val ksSakClient: KsSakClient,
    private val adressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService,
    private val journalpostBrukerService: JournalpostBrukerService,
) {
    private val toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_KONTANTSTØTTE_SØKNADER
    private val tema = Tema.KON
    private val enheterSomIkkeSkalHaAutomatiskJournalføring = listOf("4863")

    fun skalAutomatiskJournalføres(
        journalpost: Journalpost,
        fagsakId: Long,
    ): Boolean {
        if (!unleashService.isEnabled(toggleId = toggleId, defaultValue = false)) {
            return false
        }

        if (!journalpost.erKontantstøtteSøknad()) {
            return false
        }

        if (!journalpost.erDigitalKanal()) {
            return false
        }

        if (adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)) {
            return false
        }

        val personIdent = journalpostBrukerService.tilPersonIdent(journalpost.bruker!!, tema)
        val enhetId = arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(personIdent, tema).enhetId

        if (enhetId in enheterSomIkkeSkalHaAutomatiskJournalføring) {
            return false
        }

        val minialFagsak = ksSakClient.hentMinimalRestFagsak(fagsakId)

        if (minialFagsak.finnesÅpenBehandlingPåFagsak()) {
            return false
        }

        return true
    }
}
