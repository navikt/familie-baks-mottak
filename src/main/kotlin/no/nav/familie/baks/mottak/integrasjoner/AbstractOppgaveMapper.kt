package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.util.erDnummer
import no.nav.familie.baks.mottak.util.erOrgnr
import no.nav.familie.baks.mottak.util.fristFerdigstillelse
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import no.nav.familie.kontrakter.felles.oppgave.IdentGruppe
import no.nav.familie.kontrakter.felles.oppgave.OppgaveIdentV2
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.kontrakter.felles.oppgave.OpprettOppgaveRequest
import org.springframework.stereotype.Service

abstract class AbstractOppgaveMapper(
    private val enhetsnummerService: EnhetsnummerService,
    private val pdlClientService: PdlClientService,
) : IOppgaveMapper {
    override fun tilOpprettOppgaveRequest(
        oppgavetype: Oppgavetype,
        journalpost: Journalpost,
        beskrivelse: String?,
    ): OpprettOppgaveRequest {
        validerJournalpost(journalpost)
        val ident = tilOppgaveIdent(journalpost, oppgavetype)
        return OpprettOppgaveRequest(
            ident = ident,
            saksId = journalpost.sak?.fagsakId,
            journalpostId = journalpost.journalpostId,
            tema = tema,
            oppgavetype = oppgavetype,
            fristFerdigstillelse = fristFerdigstillelse(),
            beskrivelse = tilBeskrivelse(journalpost, beskrivelse),
            enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost),
            behandlingstema = hentBehandlingstemaVerdi(journalpost),
            behandlingstype = hentBehandlingstypeVerdi(journalpost),
        )
    }

    abstract fun hentBehandlingstema(journalpost: Journalpost): Behandlingstema?

    abstract fun hentBehandlingstemaVerdi(journalpost: Journalpost): String?

    abstract fun hentBehandlingstypeVerdi(journalpost: Journalpost): String?

    abstract fun hentBehandlingstype(journalpost: Journalpost): Behandlingstype?

    private fun tilOppgaveIdent(
        journalpost: Journalpost,
        oppgavetype: Oppgavetype,
    ): OppgaveIdentV2? {
        val journalpostBruker = journalpost.bruker

        if (journalpostBruker == null) {
            when (oppgavetype) {
                Oppgavetype.BehandleSak -> {
                    error("Journalpost ${journalpost.journalpostId} mangler bruker")
                }

                Oppgavetype.Journalføring -> {
                    return null
                }

                else -> {
                    // NOP
                }
            }
        }

        return when (journalpostBruker?.type) {
            BrukerIdType.FNR -> {
                hentAktørIdFraPdl(journalpostBruker.id.trim(), Tema.valueOf(journalpost.tema!!))?.let {
                    OppgaveIdentV2(
                        ident = it,
                        gruppe = IdentGruppe.AKTOERID,
                    )
                } ?: if (oppgavetype == Oppgavetype.BehandleSak) {
                    throw IntegrasjonException(
                        msg = "Fant ikke aktørId på person i PDL",
                        ident = journalpostBruker.id,
                    )
                } else {
                    null
                }
            }

            BrukerIdType.ORGNR -> {
                if (erOrgnr(journalpostBruker.id.trim())) {
                    OppgaveIdentV2(ident = journalpostBruker.id.trim(), gruppe = IdentGruppe.ORGNR)
                } else {
                    null
                }
            }

            BrukerIdType.AKTOERID -> {
                OppgaveIdentV2(ident = journalpostBruker.id.trim(), gruppe = IdentGruppe.AKTOERID)
            }

            else -> {
                null
            }
        }
    }

    private fun tilBeskrivelse(
        journalpost: Journalpost,
        beskrivelse: String?,
    ): String {
        val bindestrek =
            if (!beskrivelse.isNullOrEmpty() && !journalpost.hentHovedDokumentTittel().isNullOrEmpty()) {
                "-"
            } else {
                ""
            }

        return "${journalpost.hentHovedDokumentTittel().orEmpty()} $bindestrek ${beskrivelse.orEmpty()}".trim()
    }

    private fun hentAktørIdFraPdl(
        brukerId: String,
        tema: Tema,
    ): String? =
        try {
            pdlClientService
                .hentIdenter(brukerId, tema)
                .filter { it.gruppe == Identgruppe.AKTORID.name && !it.historisk }
                .lastOrNull()
                ?.ident
        } catch (e: IntegrasjonException) {
            null
        }

    fun erDnummerPåJournalpost(
        journalpost: Journalpost,
    ): Boolean {
        return when (journalpost.bruker?.type) {
            BrukerIdType.FNR -> erDnummer(journalpost.bruker!!.id)
            BrukerIdType.AKTOERID -> erDnummer(pdlClientService.hentPersonident(journalpost.bruker!!.id, tema).takeIf { it.isNotEmpty() } ?: return false)
            else -> false
        }
    }

    private fun validerJournalpost(journalpost: Journalpost) {
        if (journalpost.dokumenter.isNullOrEmpty()) error("Journalpost ${journalpost.journalpostId} mangler dokumenter")
    }
}

interface IOppgaveMapper {
    val tema: Tema

    fun tilOpprettOppgaveRequest(
        oppgavetype: Oppgavetype,
        journalpost: Journalpost,
        beskrivelse: String? = null,
    ): OpprettOppgaveRequest

    fun støtterTema(tema: Tema) = this.tema == tema
}

@Service
class OppgaveMapperService(
    val oppgaveMappers: Collection<IOppgaveMapper>,
) {
    fun tilOpprettOppgaveRequest(
        oppgavetype: Oppgavetype,
        journalpost: Journalpost,
        beskrivelse: String? = null,
    ): OpprettOppgaveRequest = oppgaveMappers.first { it.støtterTema(Tema.valueOf(journalpost.tema!!)) }.tilOpprettOppgaveRequest(oppgavetype, journalpost, beskrivelse)
}
