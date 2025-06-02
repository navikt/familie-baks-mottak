package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
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

    fun skalAutomatiskJournalføres(
        journalpost: Journalpost,
        brukerHarSakIInfotrygd: Boolean,
    ): Boolean {
        val kode6Og19ToggleErPå = unleashService.isEnabled(FeatureToggleConfig.AUTOMATISK_JOURNALFØR_ENHET_2103, defaultValue = false)

        val enheterSomIkkeSkalHaAutomatiskJournalføring =
            if (kode6Og19ToggleErPå) {
                listOf("4863")
            } else {
                listOf("4863", "2103")
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

        val noenHarKode6Eller19 = adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)

        if (kode6Og19ToggleErPå) {
            val søkerHarKode6Eller19 = adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpostBruker(tema, journalpost)

            if (!søkerHarKode6Eller19 && noenHarKode6Eller19) {
                return false
            }
        } else if (noenHarKode6Eller19) {
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
