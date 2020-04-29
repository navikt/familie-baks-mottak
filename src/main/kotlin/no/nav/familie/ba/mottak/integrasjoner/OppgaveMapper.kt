package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.util.erDnummer
import no.nav.familie.ba.mottak.util.fristFerdigstillelse
import no.nav.familie.kontrakter.felles.oppgave.*
import org.springframework.stereotype.Service

@Service
class OppgaveMapper(private val aktørClient: AktørClient) {

    fun mapTilOpprettOppgave(oppgavetype: Oppgavetype,
                             journalpost: Journalpost): OpprettOppgave {
        val ident = tilOppgaveIdent(journalpost)
        return OpprettOppgave(ident = ident,
                              saksId = journalpost.sak?.fagsakId,
                              journalpostId = journalpost.journalpostId,
                              tema = Tema.BAR,
                              oppgavetype = oppgavetype,
                              fristFerdigstillelse = fristFerdigstillelse(),
                              beskrivelse = "",
                              enhetsnummer = journalpost.journalforendeEnhet,
                              behandlingstema = if (erDnummer(ident.ident)) Behandlingstema.BarnetrygdEØS.value else journalpost.behandlingstema)

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