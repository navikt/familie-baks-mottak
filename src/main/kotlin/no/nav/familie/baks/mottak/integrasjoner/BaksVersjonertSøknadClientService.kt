package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknad
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknad
import org.slf4j.LoggerFactory
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service

private val logger = LoggerFactory.getLogger(BaksVersjonertSøknadClientService::class.java)

@Service
class BaksVersjonertSøknadClientService(
    private val baksVersjonertSøknadClient: BaksVersjonertSøknadClient,
) {
    @Retryable(
        value = [RuntimeException::class],
        maxRetries = 3,
        delayString = "\${retry.backoff.delay:5000}",
    )
    fun hentVersjonertBarnetrygdSøknad(
        journalpostId: String,
    ): VersjonertBarnetrygdSøknad = baksVersjonertSøknadClient.hentVersjonertBarnetrygdSøknad(journalpostId)

    @Retryable(
        value = [RuntimeException::class],
        maxRetries = 3,
        delayString = "\${retry.backoff.delay:5000}",
    )
    fun hentVersjonertKontantstøtteSøknad(
        journalpostId: String,
    ): VersjonertKontantstøtteSøknad = baksVersjonertSøknadClient.hentVersjonertKontantstøtteSøknad(journalpostId)
}
