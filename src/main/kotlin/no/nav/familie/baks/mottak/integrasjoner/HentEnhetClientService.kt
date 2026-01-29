package no.nav.familie.baks.mottak.integrasjoner

import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service

private val logger = LoggerFactory.getLogger(HentEnhetClientService::class.java)

@Service
class HentEnhetClientService(
    private val hentEnhetClient: HentEnhetClient,
) {
    @Retryable(
        value = [RuntimeException::class],
        maxRetries = 3,
        delayString = "\${retry.backoff.delay:5000}",
    )
    @Cacheable("enhet", cacheManager = "dailyCacheManager")
    fun hentEnhet(enhetId: String): Enhet = hentEnhetClient.hentEnhet(enhetId)
}
