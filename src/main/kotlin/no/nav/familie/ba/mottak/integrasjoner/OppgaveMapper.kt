package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.util.erDnummer
import no.nav.familie.ba.mottak.util.fristFerdigstillelse
import no.nav.familie.kontrakter.felles.oppgave.*
import org.springframework.stereotype.Service

@Service
class OppgaveMapper(private val aktørClient: AktørClient) {

    fun mapTilOpprettOppgave(oppgavetype: Oppgavetype,
                             journalpost: Journalpost,
                             beskrivelse: String? = null): OpprettOppgaveRequest {
        val ident = tilOppgaveIdent(journalpost, oppgavetype)
        return OpprettOppgaveRequest(ident = ident,
                saksId = journalpost.sak?.fagsakId,
                journalpostId = journalpost.journalpostId,
                tema = Tema.BAR,
                oppgavetype = oppgavetype,
                fristFerdigstillelse = fristFerdigstillelse(),
                beskrivelse = beskrivelse ?: journalpost.hentHovedDokumentTittel() ?: "",
                enhetsnummer = if (journalpost.journalforendeEnhet == "2101") "4806" else journalpost.journalforendeEnhet, //Enhet 2101 er nedlagt. Rutes til 4806
                behandlingstema = hentBehandlingstema(journalpost),
                behandlingstype = hentBehandlingstype(journalpost))
    }

    private fun tilOppgaveIdent(journalpost: Journalpost, oppgavetype: Oppgavetype): OppgaveIdentV2? {
        if (journalpost.bruker == null) {
            when (oppgavetype) {
                Oppgavetype.BehandleSak -> throw error("Journalpost ${journalpost.journalpostId} mangler bruker")
                Oppgavetype.Journalføring -> return null
            }
        }

        return when (journalpost.bruker.type) {
            BrukerIdType.FNR -> {
                OppgaveIdentV2(ident = aktørClient.hentAktørId(journalpost.bruker.id.trim()), gruppe = IdentGruppe.AKTOERID)
            }
            BrukerIdType.ORGNR -> OppgaveIdentV2(ident = journalpost.bruker.id.trim(), gruppe = IdentGruppe.ORGNR)
            BrukerIdType.AKTOERID -> OppgaveIdentV2(ident = journalpost.bruker.id.trim(), gruppe = IdentGruppe.AKTOERID)
        }
    }

    private fun hentBehandlingstema(journalpost: Journalpost): String? {
        if (journalpost.dokumenter.isNullOrEmpty()) error("Journalpost ${journalpost.journalpostId} mangler dokumenter")

        if (journalpost.bruker?.type == BrukerIdType.FNR && erDnummer(journalpost.bruker.id)) {
            return Behandlingstema.BarnetrygdEØS.value
        }

        return when (journalpost.dokumenter.firstOrNull { it.brevkode != null }?.brevkode) {
            "NAV 33-00.15" -> null
            else -> Behandlingstema.OrdinærBarnetrygd.value
        }
    }

    private fun hentBehandlingstype(journalpost: Journalpost): String? {
        if (journalpost.dokumenter.isNullOrEmpty()) error("Journalpost ${journalpost.journalpostId} mangler dokumenter")
        return if (journalpost.dokumenter.any { it.brevkode == "NAV 33-00.15" }) Behandlingstype.Utland.value else null
    }
}