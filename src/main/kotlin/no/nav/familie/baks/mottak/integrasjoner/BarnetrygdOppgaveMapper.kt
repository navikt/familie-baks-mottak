package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import org.springframework.stereotype.Service

@Service
class BarnetrygdOppgaveMapper(hentEnhetClient: HentEnhetClient, pdlClient: PdlClient) :
    AbstractOppgaveMapper(hentEnhetClient, pdlClient) {
    override val tema: Tema = Tema.BAR

    // Behandlingstema og behandlingstype settes basert på regelsettet som er dokumentert nederst her: https://confluence.adeo.no/display/TFA/Mottak+av+dokumenter
    override fun hentBehandlingstema(journalpost: Journalpost): String? {
        return when {
            erEØS(journalpost, Tema.BAR) -> Behandlingstema.BarnetrygdEØS.value
            hoveddokumentErÅrligDifferanseutbetalingAvBarnetrygd(journalpost) -> null
            else -> Behandlingstema.OrdinærBarnetrygd.value
        }
    }

    override fun hentBehandlingstype(journalpost: Journalpost): Behandlingstype? {
        return when {
            hoveddokumentErÅrligDifferanseutbetalingAvBarnetrygd(journalpost) -> Behandlingstype.Utland
            else -> null
        }
    }

    override fun hentBehandlingstypeVerdi(journalpost: Journalpost): String? = hentBehandlingstype(journalpost)?.value

    private fun hoveddokumentErÅrligDifferanseutbetalingAvBarnetrygd(journalpost: Journalpost) =
        // Brevkode "NAV 33-00.15" representerer dokumentet "Norsk sokkel - Årlig differanseutbetaling av barnetrygd"
        journalpost.dokumenter!!.firstOrNull { it.brevkode != null }?.brevkode == "NAV 33-00.15"
}
