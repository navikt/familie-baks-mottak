package no.nav.familie.baks.mottak.integrasjoner

import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service

@Service
class FamilieDokumentClientService(
    private val familieDokumentClient: FamilieDokumentClient,
) {
    @Retryable(value = [RuntimeException::class], maxRetries = 3, delayString = ("\${retry.backoff.delay:5000}"))
    fun hentVedlegg(dokumentId: String): ByteArray = familieDokumentClient.hentVedlegg(dokumentId)
}
