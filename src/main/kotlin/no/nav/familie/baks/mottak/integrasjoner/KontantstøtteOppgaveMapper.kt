package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.util.erDnummer
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

    override fun erEØS(
        journalpost: Journalpost,
    ): Boolean {
        return when (journalpost.bruker?.type) {
            BrukerIdType.FNR -> erDnummer(journalpost.bruker.id)
            BrukerIdType.AKTOERID -> erDnummer(pdlClient.hentPersonident(journalpost.bruker.id, tema))
            else -> false
        }
    }

    override fun hentBehandlingstypeVerdi(journalpost: Journalpost) = hentBehandlingstype(journalpost).value

    override fun hentBehandlingstype(journalpost: Journalpost): Behandlingstype {
        return when {
            erEØS(journalpost) -> Behandlingstype.EØS
            else -> Behandlingstype.NASJONAL
        }
    }
}
