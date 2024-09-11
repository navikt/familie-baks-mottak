package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.integrasjoner.*
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.unleash.UnleashService
import org.springframework.stereotype.Service

@Service
class AutomatiskJournalføringBarnetrygdService(
    private val unleashService: UnleashService,
    private val baSakClient: BaSakClient,
    private val arbeidsfordelingClient: ArbeidsfordelingClient,
    private val pdlClient: PdlClient,
    private val adressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService,
) {
    private val tema = Tema.BAR
    private val enheterSomIkkeSkalHaAutomatiskJournalføring = listOf("4863")

    fun skalAutomatiskJournalføres(
        journalpost: Journalpost,
        brukerHarSakIInfotrygd: Boolean,
    ): Boolean {
        val erBarnetrygdSøknad = journalpost.erBarnetrygdSøknad()

        val featureToggleForAutomatiskJournalføringSkruddPå =
            unleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER,
                defaultValue = false,
            )

        val personIdent by lazy { tilPersonIdent(journalpost.bruker!!, tema) }
        val fagsakId by lazy { baSakClient.hentFagsaknummerPåPersonident(personIdent) }
        val harÅpenBehandlingIFagsak by lazy { baSakClient.hentMinimalRestFagsak(fagsakId.toLong()).finnesÅpenBehandlingPåFagsak() }

        if (adressebeskyttelesesgraderingService.finnesAdressebeskyttelsegradringPåJournalpost(tema, journalpost)) {
            return false
        }

        val skalAutomatiskJournalføreJournalpost =
            featureToggleForAutomatiskJournalføringSkruddPå &&
                erBarnetrygdSøknad &&
                !brukerHarSakIInfotrygd &&
                arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(personIdent, tema).enhetId !in enheterSomIkkeSkalHaAutomatiskJournalføring &&
                journalpost.erDigitalKanal() &&
                !harÅpenBehandlingIFagsak

        return skalAutomatiskJournalføreJournalpost
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
