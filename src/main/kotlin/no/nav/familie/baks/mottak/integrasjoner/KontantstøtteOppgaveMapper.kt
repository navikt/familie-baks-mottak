package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import org.springframework.stereotype.Service

@Service
class KontantstøtteOppgaveMapper(
    hentEnhetClient: HentEnhetClient,
    pdlClient: PdlClient,
) : AbstractOppgaveMapper(hentEnhetClient, pdlClient) {

    override val tema: Tema = Tema.KON

    override fun hentBehandlingstema(journalpost: Journalpost): String? {
        return null
    }

    override fun hentBehandlingstype(journalpost: Journalpost): String? {
        return when {
            erEØS(journalpost) -> Behandlingstype.EØS.value
            else -> Behandlingstype.NASJONAL.value
        }
    }
}
