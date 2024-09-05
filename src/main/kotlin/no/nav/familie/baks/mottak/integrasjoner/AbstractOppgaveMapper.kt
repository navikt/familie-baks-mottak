package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
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
import no.nav.familie.unleash.UnleashService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Locale

abstract class AbstractOppgaveMapper(
    private val hentEnhetClient: HentEnhetClient,
    private val unleashService: UnleashService,
    private val enhetsnummerService: EnhetsnummerService,
    val pdlClient: PdlClient,
    val arbeidsfordelingClient: ArbeidsfordelingClient,
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
            enhetsnummer =
                if (unleashService.isEnabled(FeatureToggleConfig.BRUK_ENHETSNUMMERSERVICE)) {
                    enhetsnummerService.hentEnhetsnummer(journalpost)
                } else {
                    utledEnhetsnummer(journalpost)
                },
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
                hentAktørIdFraPdl(journalpost.bruker.id.trim(), Tema.valueOf(journalpost.tema!!))?.let {
                    OppgaveIdentV2(
                        ident = it,
                        gruppe = IdentGruppe.AKTOERID,
                    )
                } ?: if (oppgavetype == Oppgavetype.BehandleSak) {
                    throw IntegrasjonException(
                        msg = "Fant ikke aktørId på person i PDL",
                        ident = journalpost.bruker.id,
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

    private fun utledEnhetsnummer(journalpost: Journalpost): String? =
        when {
            journalpost.journalforendeEnhet == "2101" -> "4806" // Enhet 2101 er nedlagt. Rutes til 4806
            journalpost.journalforendeEnhet == "4847" -> "4817" // Enhet 4847 skal legges ned. Rutes til 4817
            journalpost.erDigitalKanal() && (journalpost.erBarnetrygdSøknad() || journalpost.erKontantstøtteSøknad()) -> hentBehandlendeEnhetForPerson(journalpost)
            journalpost.journalforendeEnhet.isNullOrBlank() -> null
            hentEnhetClient.hentEnhet(journalpost.journalforendeEnhet).status.uppercase(Locale.getDefault()) == "NEDLAGT" -> null
            hentEnhetClient.hentEnhet(journalpost.journalforendeEnhet).oppgavebehandler -> journalpost.journalforendeEnhet
            else -> {
                logger.warn("Enhet ${journalpost.journalforendeEnhet} kan ikke ta i mot oppgaver")
                null
            }
        }

    private fun hentBehandlendeEnhetForPerson(journalpost: Journalpost): String? =
        if (journalpost.bruker != null) {
            val personIdentPåJournalpost = tilPersonIdent(journalpost.bruker, this.tema)
            val behandlendeEnhetPåIdent = arbeidsfordelingClient.hentBehandlendeEnhetPåIdent(personIdentPåJournalpost, this.tema)

            behandlendeEnhetPåIdent.enhetId
        } else {
            logger.warn("Fant ikke bruker på journalpost ved forsøk på henting av behandlende enhet")
            null
        }

    private fun tilPersonIdent(
        bruker: Bruker,
        tema: Tema,
    ): String =
        when (bruker.type) {
            BrukerIdType.AKTOERID -> pdlClient.hentPersonident(bruker.id, tema)
            else -> bruker.id
        }

    private fun hentAktørIdFraPdl(
        brukerId: String,
        tema: Tema,
    ): String? =
        try {
            pdlClient
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
            BrukerIdType.FNR -> erDnummer(journalpost.bruker.id)
            BrukerIdType.AKTOERID -> erDnummer(pdlClient.hentPersonident(journalpost.bruker.id, tema).takeIf { it.isNotEmpty() } ?: return false)
            else -> false
        }
    }

    private fun validerJournalpost(journalpost: Journalpost) {
        if (journalpost.dokumenter.isNullOrEmpty()) error("Journalpost ${journalpost.journalpostId} mangler dokumenter")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
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
