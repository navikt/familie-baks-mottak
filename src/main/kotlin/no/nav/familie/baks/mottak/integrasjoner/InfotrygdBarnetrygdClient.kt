package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.felles.tokenklient.entraid.EntraIDRestClientFactory
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkRequest
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.body
import java.net.URI
import no.nav.familie.kontrakter.ba.infotrygd.Stønad as StønadDto

@Component
class InfotrygdBarnetrygdClient(
    @Value("\${FAMILIE_BA_INFOTRYGD_API_URL}/infotrygd/barnetrygd") private val clientUri: URI,
    @Value("\${FAMILIE_BA_INFOTRYGD_SCOPE}") private val infotrygdScope: String,
    entraIDRestClientFactory: EntraIDRestClientFactory,
) {
    private val restClient = entraIDRestClientFactory.lagMaskinTilMaskinRestKlient(infotrygdScope)

    fun hentVedtak(søkersIdenter: List<String>): InfotrygdSøkResponse<StønadDto> =
        infotrygdResponseFra(
            request = {
                restClient
                    .post()
                    .uri(uri("stonad?historikk=true"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(InfotrygdSøkRequest(søkersIdenter))
                    .retrieve()
                    .body<InfotrygdSøkResponse<StønadDto>>()!!
            },
            onFailure = { ex -> IntegrasjonException("Feil ved uthenting av vedtak fra infotrygd", ex, uri("stonad?historikk=true")) },
        )

    private fun <T> infotrygdResponseFra(
        request: () -> InfotrygdSøkResponse<T>,
        onFailure: (Throwable) -> RuntimeException,
    ): InfotrygdSøkResponse<T> = runCatching(request).getOrElse { throw onFailure(it) }

    private fun uri(endepunkt: String) = URI.create("$clientUri/$endepunkt")
}
