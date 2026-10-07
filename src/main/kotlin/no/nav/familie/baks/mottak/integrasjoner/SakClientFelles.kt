package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.Ressurs
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.net.URI

internal fun RestClient.låsOppFagsak(
    sakServiceUri: String,
    fagsakId: Long,
    begrunnelse: String,
): RestMinimalFagsak {
    val uri = URI.create("$sakServiceUri/fagsaker/$fagsakId/laas-opp")

    return runCatching {
        patch()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(LåsOppFagsakRequestDto(begrunnelse = begrunnelse))
            .retrieve()
            .body<Ressurs<RestMinimalFagsak>>()!!
    }.fold(
        onSuccess = { it.data ?: throw IntegrasjonException(it.melding, null, uri) },
        onFailure = { throw IntegrasjonException("Feil ved opplåsing av fagsak", it, uri) },
    )
}
