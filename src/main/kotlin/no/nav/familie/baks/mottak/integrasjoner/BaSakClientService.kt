package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.domene.NyBehandling
import org.slf4j.LoggerFactory
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service

@Service
class BaSakClientService(
    private val baSakClient: BaSakClient,
) {
    private val logger = LoggerFactory.getLogger(BaSakClientService::class.java)

    @Retryable(
        value = [RuntimeException::class],
        maxRetries = 3,
        delayString = "\${retry.backoff.delay:5000}",
    )
    fun sendTilSak(nyBehandling: NyBehandling) {
        logger.info("BaSakClientService: sender søknad til BaSakClient.sendTilSak")
        baSakClient.sendTilSak(nyBehandling)
    }
}
