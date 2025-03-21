package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadRepository
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.harEøsSteg
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import org.springframework.stereotype.Service

@Service
class KontantstøtteOppgaveMapper(
    enhetsnummerService: EnhetsnummerService,
    pdlClient: PdlClient,
    val kontantstøtteSøknadRepository: KontantstøtteSøknadRepository,
    private val unleashService: UnleashNextMedContextService,
) : AbstractOppgaveMapper(
        enhetsnummerService = enhetsnummerService,
        pdlClient = pdlClient,
    ) {
    override val tema: Tema = Tema.KON

    override fun hentBehandlingstema(journalpost: Journalpost): Behandlingstema? = null

    override fun hentBehandlingstemaVerdi(journalpost: Journalpost) = hentBehandlingstema(journalpost)?.value

    override fun hentBehandlingstypeVerdi(journalpost: Journalpost) = hentBehandlingstype(journalpost).value

    override fun hentBehandlingstype(journalpost: Journalpost): Behandlingstype =
        when {
            journalpost.harKontantstøtteSøknad() && journalpost.erDigitalKanal() ->
                if (utledBehandlingKategoriFraSøknad(journalpost) == BehandlingKategori.EØS) {
                    Behandlingstype.EØS
                } else {
                    Behandlingstype.NASJONAL
                }

            erDnummerPåJournalpost(journalpost) -> Behandlingstype.EØS
            journalpost.harKlage() && unleashService.isEnabled(FeatureToggleConfig.SETT_BEHANDLINGSTEMA_OG_BEHANDLINGSTYPE_FOR_KLAGE, false) -> Behandlingstype.Klage
            else -> Behandlingstype.NASJONAL
        }

    fun utledBehandlingKategoriFraSøknad(journalpost: Journalpost): BehandlingKategori {
        check(journalpost.harKontantstøtteSøknad()) { "Journalpost m/ id ${journalpost.journalpostId} er ikke en kontantstøtte søknad" }

        val søknad = kontantstøtteSøknadRepository.getByJournalpostId(journalpost.journalpostId)

        return when {
            søknad.harEøsSteg() -> BehandlingKategori.EØS
            else -> BehandlingKategori.NASJONAL
        }
    }
}
