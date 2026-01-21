package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknad
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknad
import no.nav.familie.restklient.client.AbstractRestClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.net.URI

private val logger = LoggerFactory.getLogger(BaksVersjonertSøknadClient::class.java)

@Component
class BaksVersjonertSøknadClient(
    @param:Value("\${FAMILIE_INTEGRASJONER_API_URL}")
    private val integrasjonerServiceUri: URI,
    @Qualifier("clientCredentials") restOperations: RestOperations,
) : AbstractRestClient(restOperations, "integrasjon") {
    fun hentVersjonertBarnetrygdSøknad(
        journalpostId: String,
    ): VersjonertBarnetrygdSøknad {
        val uri = URI.create("$integrasjonerServiceUri/baks/versjonertsoknad/ba/$journalpostId")
        logger.debug("henter søknad for barnetrygd med journalpostId: $journalpostId")

        return runCatching {
            getForEntity<Ressurs<VersjonertBarnetrygdSøknad>>(uri)
        }.fold(
            onSuccess = { it.data ?: throw IntegrasjonException(it.melding, uri = uri) },
            onFailure = { throw IntegrasjonException("Henting av søknad for barnetrygd feilet. journalpostId: $journalpostId", it, uri) },
        )
    }

    fun hentVersjonertKontantstøtteSøknad(
        journalpostId: String,
    ): VersjonertKontantstøtteSøknad {
        val uri = URI.create("$integrasjonerServiceUri/baks/versjonertsoknad/ks/$journalpostId")
        logger.debug("henter søknad for kontantstøtte med journalpostId: $journalpostId")

        return runCatching {
            getForEntity<Ressurs<VersjonertKontantstøtteSøknad>>(uri)
        }.fold(
            onSuccess = { it.data ?: throw IntegrasjonException(it.melding, uri = uri) },
            onFailure = { throw IntegrasjonException("Henting av søknad for kontantstøtte feilet. journalpostId: $journalpostId.", it, uri) },
        )
    }
}
