package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.harEøsSteg
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import no.nav.familie.unleash.UnleashService
import org.springframework.stereotype.Service

@Service
class BarnetrygdOppgaveMapper(
    hentEnhetClient: HentEnhetClient,
    unleashService: UnleashService,
    enhetsnummerService: EnhetsnummerService,
    arbeidsfordelingClient: ArbeidsfordelingClient,
    pdlClient: PdlClient,
    val søknadRepository: SøknadRepository,
) : AbstractOppgaveMapper(hentEnhetClient, unleashService, enhetsnummerService, pdlClient, arbeidsfordelingClient) {
    override val tema: Tema = Tema.BAR

    // Behandlingstema og behandlingstype settes basert på regelsettet som er dokumentert nederst her: https://confluence.adeo.no/display/TFA/Mottak+av+dokumenter
    override fun hentBehandlingstema(journalpost: Journalpost): Behandlingstema? =
        when {
            journalpost.erBarnetrygdSøknad() && journalpost.erDigitalKanal() ->
                if (utledBehandlingUnderkategoriFraSøknad(journalpost) == BehandlingUnderkategori.UTVIDET) {
                    Behandlingstema.UtvidetBarnetrygd
                } else {
                    Behandlingstema.OrdinærBarnetrygd
                }

            erDnummerPåJournalpost(journalpost) -> Behandlingstema.BarnetrygdEØS
            hoveddokumentErÅrligDifferanseutbetalingAvBarnetrygd(journalpost) -> null
            else -> Behandlingstema.OrdinærBarnetrygd
        }

    override fun hentBehandlingstemaVerdi(journalpost: Journalpost) = hentBehandlingstema(journalpost)?.value

    override fun hentBehandlingstype(journalpost: Journalpost): Behandlingstype? =
        when {
            journalpost.erBarnetrygdSøknad() && journalpost.erDigitalKanal() ->
                if (utledBehandlingKategoriFraSøknad(journalpost) == BehandlingKategori.EØS) {
                    Behandlingstype.EØS
                } else {
                    Behandlingstype.NASJONAL
                }
            hoveddokumentErÅrligDifferanseutbetalingAvBarnetrygd(journalpost) -> Behandlingstype.Utland
            else -> null
        }

    override fun hentBehandlingstypeVerdi(journalpost: Journalpost): String? = hentBehandlingstype(journalpost)?.value

    fun utledBehandlingKategoriFraSøknad(journalpost: Journalpost): BehandlingKategori {
        check(journalpost.erBarnetrygdSøknad()) { "Journalpost m/ id ${journalpost.journalpostId} er ikke en barnetrygd søknad" }
        val søknad = søknadRepository.getByJournalpostId(journalpost.journalpostId)

        return when {
            søknad.harEøsSteg() -> BehandlingKategori.EØS
            else -> BehandlingKategori.NASJONAL
        }
    }

    fun utledBehandlingUnderkategoriFraSøknad(journalpost: Journalpost) =
        when {
            journalpost.erBarnetrygdUtvidetSøknad() -> BehandlingUnderkategori.UTVIDET
            else -> BehandlingUnderkategori.ORDINÆR
        }

    private fun hoveddokumentErÅrligDifferanseutbetalingAvBarnetrygd(journalpost: Journalpost) =
        // Brevkode "NAV 33-00.15" representerer dokumentet "Norsk sokkel - Årlig differanseutbetaling av barnetrygd"
        journalpost.dokumenter!!.firstOrNull { it.brevkode != null }?.brevkode == "NAV 33-00.15"
}
