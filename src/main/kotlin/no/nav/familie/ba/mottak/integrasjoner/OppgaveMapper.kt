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
                              behandlingstema = hentBehandlingstema(ident, journalpost),
                              behandlingstype = hentBehandlingstype(journalpost))
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

    private fun hentBehandlingstema(ident: OppgaveIdent, journalpost: Journalpost): String? {
        if (journalpost.dokumenter.isNullOrEmpty()) throw error("Journalpost ${journalpost.journalpostId} mangler dokumenter")

        if (erDnummer(ident.ident)) return Behandlingstema.BarnetrygdEØS.value

        return when (journalpost.dokumenter.firstOrNull { it.brevkode != null }?.brevkode) {
            "NAV 33-00.07" -> Behandlingstema.OrdinærBarnetrygd.value
            "NAV 33-00.09" -> Behandlingstema.UtvidetBarnetrygd.value
            else -> journalpost.behandlingstema
        }
    }

    private fun hentBehandlingstype(journalpost: Journalpost): String? {
        if (journalpost.dokumenter.isNullOrEmpty()) throw error("Journalpost ${journalpost.journalpostId} mangler dokumenter")
        return if (journalpost.dokumenter.any { it.brevkode == "NAV 33-00.15" }) Behandlingstype.Utland.value else null
    }
}