package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.integrasjoner.ArbeidsfordelingClient
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.BarnetrygdOppgaveMapper
import no.nav.familie.baks.mottak.integrasjoner.finnesÅpenBehandlingPåFagsak
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AutomatiskJournalføringBarnetrygdService(
    private val baSakClient: BaSakClient,
    private val arbeidsfordelingClient: ArbeidsfordelingClient,
    private val adressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService,
    private val journalpostBrukerService: JournalpostBrukerService,
    private val barnetrygdOppgaveMapper: BarnetrygdOppgaveMapper,
) {
    private val log: Logger = LoggerFactory.getLogger(AutomatiskJournalføringBarnetrygdService::class.java)
    private val tema = Tema.BAR

    fun skalAutomatiskJournalføres(
        journalpost: Journalpost,
        brukerHarSakIInfotrygd: Boolean,
    ): Boolean {
        val årsakTilManuellJournalføring = finnÅrsakTilManuellJournalføring(journalpost, brukerHarSakIInfotrygd)

        if (årsakTilManuellJournalføring != null) {
            log.info(
                "Journalpost ${journalpost.journalpostId} journalføres ikke automatisk. Årsak: $årsakTilManuellJournalføring",
            )
            return false
        }

        return true
    }

    private fun finnÅrsakTilManuellJournalføring(
        journalpost: Journalpost,
        brukerHarSakIInfotrygd: Boolean,
    ): String? {
        val enheterSomIkkeSkalHaAutomatiskJournalføring = listOf("4863")

        if (!journalpost.harBarnetrygdSøknad()) {
            return "Journalposten inneholder ingen barnetrygdsøknad"
        }

        if (brukerHarSakIInfotrygd) {
            return "Bruker har sak i Infotrygd"
        }

        if (!journalpost.erDigitalKanal()) {
            return "Journalposten er ikke mottatt via digital kanal"
        }

        val antallDokumenterUtenTittel = journalpost.dokumenter.orEmpty().count { it.tittel.isNullOrBlank() }

        if (antallDokumenterUtenTittel > 0) {
            return "Journalposten har $antallDokumenterUtenTittel dokument(er) uten tittel og kan ikke ferdigstilles av Joark"
        }

        val noenHarKode6Eller19 = adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)

        val søkerHarKode6Eller19 = adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpostBruker(tema, journalpost)

        if (!søkerHarKode6Eller19 && noenHarKode6Eller19) {
            return "En annen person enn søker har strengt fortrolig adressebeskyttelse"
        }

        val bruker = journalpost.bruker!!

        if (bruker.type == BrukerIdType.ORGNR) {
            return "Journalpostbruker er en organisasjon"
        }

        val personIdent = journalpostBrukerService.tilPersonIdent(bruker, tema)
        val behandlingstype = barnetrygdOppgaveMapper.hentBehandlingstype(journalpost)
        val enhetId = arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(personIdent, tema, behandlingstype).enhetId

        if (enhetId in enheterSomIkkeSkalHaAutomatiskJournalføring) {
            return "Behandlende enhet $enhetId skal ikke ha automatisk journalføring"
        }

        val fagsakId = baSakClient.hentFagsaknummerPåPersonident(personIdent)
        val minimalFagsak = baSakClient.hentMinimalRestFagsak(fagsakId)

        if (minimalFagsak.finnesÅpenBehandlingPåFagsak()) {
            return "Det finnes allerede en åpen behandling på fagsak $fagsakId"
        }

        return null
    }
}
