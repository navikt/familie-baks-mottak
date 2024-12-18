package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.integrasjoner.ArbeidsfordelingClient
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.finnesÅpenBehandlingPåFagsak
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
    private val toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER
    private val tema = Tema.BAR
    private val enheterSomIkkeSkalHaAutomatiskJournalføring = listOf("4863", "2103")

    fun skalAutomatiskJournalføres(
        journalpost: Journalpost,
        brukerHarSakIInfotrygd: Boolean,
        fagsakId: Long,
    ): Boolean {
        if (!unleashService.isEnabled(toggleId = toggleId, defaultValue = false)) {
            return false
        }

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

        val personIdent = journalpostBrukerService.tilPersonIdent(journalpost.bruker!!, tema)
        val enhetId = arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(personIdent, tema).enhetId

        if (enhetId in enheterSomIkkeSkalHaAutomatiskJournalføring) {
            return false
        }

        val minialFagsak = baSakClient.hentMinimalRestFagsak(fagsakId)

        if (minialFagsak.finnesÅpenBehandlingPåFagsak()) {
            return false
        }

        return true
    }
}
