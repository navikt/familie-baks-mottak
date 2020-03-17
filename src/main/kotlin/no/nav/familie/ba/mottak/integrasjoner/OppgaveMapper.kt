package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.oppgave.*
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class OppgaveMapper(private val aktørClient: AktørClient) {

    fun mapTilOpprettOppgave(oppgavetype: Oppgavetype,
                             journalpost: Journalpost,
                             behandlingstema: String? = null): OpprettOppgave {
        val ident = tilOppgaveIdent(journalpost)
        return OpprettOppgave(ident = ident,
                              saksId = journalpost.sak?.fagsakId,
                              journalpostId = journalpost.journalpostId,
                              tema = Tema.BAR,
                              oppgavetype = oppgavetype,
                              fristFerdigstillelse = LocalDate.now()
                                      .plusDays(2), //TODO få denne til å funke på helg og eventuellle andre helligdager
                              beskrivelse = "",
                              enhetsnummer = journalpost.journalforendeEnhet,
                              behandlingstema = behandlingstema ?: journalpost.behandlingstema)

    }

    private fun tilOppgaveIdent(journalpost: Journalpost): OppgaveIdent {
        journalpost.bruker?.id ?: throw error("Journalpost ${journalpost.journalpostId} mangler bruker")
        return when (journalpost.bruker.type) {
            BrukerIdType.FNR -> {
                OppgaveIdent(ident = aktørClient.hentAktørId(journalpost.bruker.id), type = IdentType.Aktør)
            }
            BrukerIdType.ORGNR -> OppgaveIdent(ident = journalpost.bruker.id, type = IdentType.Organisasjon)
            BrukerIdType.AKTOERID -> OppgaveIdent(ident = journalpost.bruker.id, type = IdentType.Aktør)
        }
    }
}