package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.util.erDnummer
import no.nav.familie.baks.mottak.util.erOrgnr
import no.nav.familie.baks.mottak.util.fristFerdigstillelse
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import no.nav.familie.kontrakter.felles.oppgave.IdentGruppe
import no.nav.familie.kontrakter.felles.oppgave.OppgaveIdentV2
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.kontrakter.felles.oppgave.OpprettOppgaveRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Locale

private val logger = LoggerFactory.getLogger(OppgaveMapper::class.java)

@Service
class OppgaveMapper(
    private val hentEnhetClient: HentEnhetClient,
    private val pdlClient: PdlClient
) {

    fun mapTilOpprettOppgave(
        oppgavetype: Oppgavetype,
        journalpost: Journalpost,
        beskrivelse: String? = null
    ): OpprettOppgaveRequest {
        val ident = tilOppgaveIdent(journalpost, oppgavetype)
        val tema = runCatching { Tema.valueOf(journalpost.tema!!) }.getOrElse { exception ->
            throw RuntimeException(
                "Feil ved mapping til OpprettOppgaveRequest. Tema for journalpost er tomt eller ugyldig: ${journalpost.tema}",
                exception
            )
        }
        return OpprettOppgaveRequest(
            ident = ident,
            saksId = journalpost.sak?.fagsakId,
            journalpostId = journalpost.journalpostId,
            tema = tema,
            oppgavetype = oppgavetype,
            fristFerdigstillelse = fristFerdigstillelse(),
            beskrivelse = tilBeskrivelse(journalpost, beskrivelse),
            enhetsnummer = utledEnhetsnummer(journalpost),
            behandlingstema = hentBehandlingstema(journalpost, tema),
            behandlingstype = hentBehandlingstype(journalpost)
        )
    }

    private fun tilOppgaveIdent(journalpost: Journalpost, oppgavetype: Oppgavetype): OppgaveIdentV2? {
        if (journalpost.bruker == null) {
            when (oppgavetype) {
                Oppgavetype.BehandleSak -> error("Journalpost ${journalpost.journalpostId} mangler bruker")
                Oppgavetype.Journalføring -> return null
                else -> {
                    // NOP
                }
            }
        }

        return when (journalpost.bruker?.type) {
            BrukerIdType.FNR -> {
                hentAktørIdFraPdl(journalpost.bruker.id.trim())?.let {
                    OppgaveIdentV2(
                        ident = it,
                        gruppe = IdentGruppe.AKTOERID
                    )
                }
                    ?: if (oppgavetype == Oppgavetype.BehandleSak) {
                        throw IntegrasjonException(
                            msg = "Fant ikke aktørId på person i PDL",
                            ident = journalpost.bruker.id
                        )
                    } else {
                        null
                    }
            }

            BrukerIdType.ORGNR -> {
                if (erOrgnr(journalpost.bruker.id.trim())) {
                    OppgaveIdentV2(ident = journalpost.bruker.id.trim(), gruppe = IdentGruppe.ORGNR)
                } else {
                    null
                }
            }

            BrukerIdType.AKTOERID -> OppgaveIdentV2(ident = journalpost.bruker.id.trim(), gruppe = IdentGruppe.AKTOERID)
            else -> null
        }
    }

    private fun tilBeskrivelse(journalpost: Journalpost, beskrivelse: String?): String {
        val bindestrek = if (!beskrivelse.isNullOrEmpty() && !journalpost.hentHovedDokumentTittel().isNullOrEmpty()) {
            "-"
        } else {
            ""
        }

        return "${journalpost.hentHovedDokumentTittel().orEmpty()} $bindestrek ${beskrivelse.orEmpty()}".trim()
    }

    private fun hentBehandlingstema(journalpost: Journalpost, tema: Tema): String? {
        if (journalpost.dokumenter.isNullOrEmpty()) error("Journalpost ${journalpost.journalpostId} mangler dokumenter")

        if (journalpost.bruker?.type == BrukerIdType.FNR && erDnummer(journalpost.bruker.id)) {
            return when (tema) {
                Tema.BAR -> Behandlingstema.BarnetrygdEØS.value
                else -> Behandlingstema.KontantstøtteEØS.value
            }
        }

        return when (journalpost.dokumenter.firstOrNull { it.brevkode != null }?.brevkode) {
            // Tilleggsskjema ved krav om utbetaling av barnetrygd og/eller kontantstøtte på grunnlag av regler om eksport etter EØS-avtalen
            "NAV 33-00.15" -> null
            else -> when (tema) {
                Tema.BAR -> Behandlingstema.OrdinærBarnetrygd.value
                else -> Behandlingstema.Kontantstøtte.value
            }
        }
    }

    private fun hentBehandlingstype(journalpost: Journalpost): String? {
        if (journalpost.dokumenter.isNullOrEmpty()) error("Journalpost ${journalpost.journalpostId} mangler dokumenter")
        return if (journalpost.dokumenter.any { it.brevkode == "NAV 33-00.15" }) Behandlingstype.Utland.value else null
    }

    private fun utledEnhetsnummer(journalpost: Journalpost): String? {
        return when {
            journalpost.journalforendeEnhet == "2101" -> "4806" // Enhet 2101 er nedlagt. Rutes til 4806
            journalpost.journalforendeEnhet == "4847" -> "4817" // Enhet 4847 skal legges ned. Rutes til 4817
            journalpost.journalforendeEnhet.isNullOrBlank() -> null
            hentEnhetClient.hentEnhet(journalpost.journalforendeEnhet).status.uppercase(Locale.getDefault()) == "NEDLAGT" -> null
            hentEnhetClient.hentEnhet(journalpost.journalforendeEnhet).oppgavebehandler -> journalpost.journalforendeEnhet
            else -> {
                logger.warn("Enhet ${journalpost.journalforendeEnhet} kan ikke ta i mot oppgaver")
                null
            }
        }
    }

    private fun hentAktørIdFraPdl(brukerId: String): String? {
        return try {
            pdlClient.hentIdenter(brukerId).filter { it.gruppe == Identgruppe.AKTORID.name && !it.historisk }
                .lastOrNull()?.ident
        } catch (e: IntegrasjonException) {
            null
        }
    }
}
