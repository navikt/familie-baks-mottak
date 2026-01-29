package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service

@Service
class OppgaveClientService
    @Autowired
    constructor(
        private val oppgaveClient: OppgaveClient,
    ) {
        fun opprettJournalføringsoppgave(
            journalpost: Journalpost,
            beskrivelse: String? = null,
        ): OppgaveResponse = oppgaveClient.opprettJournalføringsoppgave(journalpost, beskrivelse)

        @Retryable(
            value = [RuntimeException::class],
            maxRetries = 3,
            delayString = "\${retry.backoff.delay:5000}",
        )
        fun opprettVurderLivshendelseOppgave(dto: OppgaveVurderLivshendelseDto): OppgaveResponse = oppgaveClient.opprettVurderLivshendelseOppgave(dto)

        @Retryable(
            value = [RuntimeException::class],
            maxRetries = 3,
            delayString = "\${retry.backoff.delay:5000}",
        )
        fun oppdaterOppgaveBeskrivelse(
            patchOppgave: Oppgave,
            beskrivelse: String,
        ): OppgaveResponse = oppgaveClient.oppdaterOppgaveBeskrivelse(patchOppgave, beskrivelse)

        @Retryable(
            value = [RuntimeException::class],
            maxRetries = 3,
            delayString = "\${retry.backoff.delay:5000}",
        )
        fun finnOppgaver(
            journalpostId: String,
            oppgavetype: Oppgavetype?,
        ): List<Oppgave> = oppgaveClient.finnOppgaver(journalpostId, oppgavetype)

        @Retryable(
            value = [RuntimeException::class],
            maxRetries = 3,
            delayString = "\${retry.backoff.delay:5000}",
        )
        fun finnOppgaverPåAktørId(
            aktørId: String,
            oppgavetype: Oppgavetype,
            tema: Tema,
        ): List<Oppgave> = oppgaveClient.finnOppgaverPåAktørId(aktørId, oppgavetype, tema)
    }
