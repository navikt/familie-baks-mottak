package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import org.springframework.stereotype.Service

@Service
class KontantstøtteOppgaveMapper(
    hentEnhetClient: HentEnhetClient,
    pdlClient: PdlClient,
) : AbstractOppgaveMapper(hentEnhetClient, pdlClient) {
    override val tema: Tema = Tema.KON

    override fun hentBehandlingstema(journalpost: Journalpost): Behandlingstema? {
        return null
    }

    override fun hentBehandlingstemaVerdi(journalpost: Journalpost) = hentBehandlingstema(journalpost)?.value

    override fun hentBehandlingstypeVerdi(journalpost: Journalpost) = hentBehandlingstype(journalpost).value

    override fun hentBehandlingstype(journalpost: Journalpost): Behandlingstype {
        return when {
            erEØS(journalpost) -> Behandlingstype.EØS
            else -> Behandlingstype.NASJONAL
        }
    }
}
