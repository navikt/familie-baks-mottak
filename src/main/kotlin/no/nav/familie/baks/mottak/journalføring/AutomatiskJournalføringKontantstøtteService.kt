package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.ArbeidsfordelingClient
import no.nav.familie.baks.mottak.integrasjoner.Journalpost
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.erDigitalKanal
import no.nav.familie.baks.mottak.integrasjoner.erKontantstøtteSøknad
import no.nav.familie.baks.mottak.integrasjoner.finnesÅpenBehandlingPåFagsak
import no.nav.familie.kontrakter.felles.Tema
import org.springframework.stereotype.Service

@Service
class AutomatiskJournalføringKontantstøtteService(
    private val unleashService: UnleashNextMedContextService,
    private val arbeidsfordelingClient: ArbeidsfordelingClient,
    private val ksSakClient: KsSakClient,
    private val adressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService,
    private val journalpostBrukerService: JournalpostBrukerService,
) {
    private val tema = Tema.KON
    private val enheterSomIkkeSkalHaAutomatiskJournalføring = listOf("4863")

    fun skalAutomatiskJournalføres(
        journalpost: Journalpost,
        brukerHarSakIInfotrygd: Boolean,
        fagsakId: Long,
    ): Boolean {
        val erKontantstøtteSøknad = journalpost.erKontantstøtteSøknad()

        val featureToggleForAutomatiskJournalføringSkruddPå =
            unleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_KONTANTSTØTTE_SØKNADER,
                defaultValue = false,
            )

        val personIdent by lazy { journalpostBrukerService.tilPersonIdent(journalpost.bruker!!, tema) }
        val harÅpenBehandlingIFagsak by lazy { ksSakClient.hentMinimalRestFagsak(fagsakId).finnesÅpenBehandlingPåFagsak() }

        if (adressebeskyttelesesgraderingService.finnesAdressebeskyttelsegradringPåJournalpost(tema, journalpost)) {
            return false
        }

        return featureToggleForAutomatiskJournalføringSkruddPå &&
            erKontantstøtteSøknad &&
            !brukerHarSakIInfotrygd &&
            journalpost.erDigitalKanal() &&
            arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(personIdent, tema).enhetId !in enheterSomIkkeSkalHaAutomatiskJournalføring &&
            !harÅpenBehandlingIFagsak
    }
}
