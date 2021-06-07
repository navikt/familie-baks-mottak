package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.util.erDnummer
import no.nav.familie.ba.mottak.util.erOrgnr
import no.nav.familie.ba.mottak.util.fristFerdigstillelse
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.oppgave.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

private val logger = LoggerFactory.getLogger(OppgaveMapper::class.java)

@Service
class OppgaveMapper(private val aktørClient: AktørClient,
                    private val hentEnhetClient: HentEnhetClient) {

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
                                     enhetsnummer = utledEnhetsnummer(journalpost),
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
            BrukerIdType.ORGNR -> {
                if (erOrgnr(journalpost.bruker.id.trim())) {
                    OppgaveIdentV2(ident = journalpost.bruker.id.trim(), gruppe = IdentGruppe.ORGNR)
                } else null
            }
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

    private fun utledEnhetsnummer(journalpost: Journalpost): String? {
        return when {
            journalpost.journalforendeEnhet == "2101" -> "4806" //Enhet 2101 er nedlagt. Rutes til 4806
            journalpost.journalforendeEnhet == "4847" -> "4817" //Enhet 4847 skal legges ned. Rutes til 4817
                    journalpost.journalforendeEnhet.isNullOrBlank() -> null
            hentEnhetClient.hentEnhet(journalpost.journalforendeEnhet).oppgavebehandler -> journalpost.journalforendeEnhet
            else -> {
                logger.warn("Enhet ${journalpost.journalforendeEnhet} kan ikke ta i mot oppgaver")
                null
            }
        }
    }
}