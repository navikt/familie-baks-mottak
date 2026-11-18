package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.integrasjoner.ArbeidsfordelingClient
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.BarnetrygdOppgaveMapper
import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.baks.mottak.integrasjoner.finnesÅpenBehandlingPåFagsak
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class AutomatiskJournalføringBarnetrygdService(
    private val baSakClient: BaSakClient,
    private val arbeidsfordelingClient: ArbeidsfordelingClient,
    private val adressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService,
    private val journalpostBrukerService: JournalpostBrukerService,
    private val barnetrygdOppgaveMapper: BarnetrygdOppgaveMapper,
    private val pdlClientService: PdlClientService,
) {
    private val tema = Tema.BAR

    fun skalAutomatiskJournalføres(journalpost: Journalpost): Boolean {
        val enheterSomIkkeSkalHaAutomatiskJournalføring = listOf("4863")

        if (!journalpost.harBarnetrygdSøknad()) {
            return false
        }

        if (!journalpost.erDigitalKanal()) {
            return false
        }

        val noenHarKode6Eller19 = adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)

        val søkerHarKode6Eller19 = adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpostBruker(tema, journalpost)

        if (!søkerHarKode6Eller19 && noenHarKode6Eller19) {
            return false
        }

        val bruker = journalpost.bruker!!

        if (bruker.type == BrukerIdType.ORGNR) {
            return false
        }

        val personIdent = journalpostBrukerService.tilPersonIdent(bruker, tema)

        if (!erSøkerMyndig(personIdent, journalpost.journalpostId)) {
            return false
        }

        val behandlingstype = barnetrygdOppgaveMapper.hentBehandlingstype(journalpost)
        val enhetId = arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(personIdent, tema, behandlingstype).enhetId

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

    private fun erSøkerMyndig(
        personIdent: String,
        journalpostId: String,
    ): Boolean {
        val fødselsdato =
            pdlClientService
                .hentPerson(personIdent, "hentperson-fødselsdato", tema)
                .fødsel
                .firstOrNull()
                ?.fødselsdato ?: error("Fant ikke fødselsdato for søker på journalpost $journalpostId")

        return !LocalDate.now().isBefore(fødselsdato.plusYears(18))
    }
}
