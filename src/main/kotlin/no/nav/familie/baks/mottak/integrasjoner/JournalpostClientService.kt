package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import org.slf4j.LoggerFactory
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service

private val logger = LoggerFactory.getLogger(JournalpostClient::class.java)

@Service
class JournalpostClientService(
    private val journalpostClient: JournalpostClient,
) {
    @Retryable(
        value = [RuntimeException::class],
        maxRetries = 3,
        delayString = "\${retry.backoff.delay:5000}",
    )
    fun hentJournalpost(journalpostId: String): Journalpost = journalpostClient.hentJournalpost(journalpostId)
}
