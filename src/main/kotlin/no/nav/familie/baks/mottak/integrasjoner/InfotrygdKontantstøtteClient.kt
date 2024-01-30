package no.nav.familie.baks.mottak.integrasjoner

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.objectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import org.springframework.web.client.postForObject
import org.springframework.web.util.UriUtils.encodePath
import java.net.URI
import java.time.YearMonth

@Service
class InfotrygdKontantstøtteClient(
    @Qualifier("clientCredentials") restOperations: RestOperations,
    @Value("\${FAMILIE_KS_INFOTRYGD_API_URL}/api") private val clientUri: URI,
    private val environment: Environment,
) :
    AbstractRestClient(restOperations, "familie-ks-infotrygd") {
    fun harKontantstøtteIInfotrygd(
        barnasIdenter: List<String>,
    ): Boolean {
        return infotrygdResponseFra(
            request = {
                operations.postForObject(
                    uri("harLopendeKontantstotteIInfotrygd"),
                    InnsynRequest(barnasIdenter),
                )
            },
            onFailure = { ex ->
                IntegrasjonException(
                    "Feil ved søk etter stønad i infotrygd.",
                    ex,
                    uri("harLopendeKontantstotteIInfotrygd"),
                )
            },
        )
    }

    fun hentPerioderMedKontantstotteIInfotrygdByBarn(
        barnasIdenter: List<String>,
    ): InnsynResponse {
        val response: String = postForEntity(uri("hentPerioderMedKontantstotteIInfotrygdByBarn"), InnsynRequest(barnasIdenter))
        return objectMapper.readValue(response, InnsynResponse::class.java)
    }

    private fun <T> infotrygdResponseFra(
        request: () -> T,
        onFailure: (Throwable) -> RuntimeException,
    ): T = runCatching(request).getOrElse { throw onFailure(it) }
    private fun uri(endepunkt: String) = URI.create(encodePath("$clientUri/$endepunkt", "UTF-8"))
}

data class InnsynRequest(
    val barn: List<String>,
)

data class InnsynResponse(
    val data: List<StonadDto>,
)

data class StonadDto(
    val fnr: String,
    val fom: String?,
    val tom: String?,
    val belop: Int?,
    val barn: List<BarnDto>,
)

data class BarnDto(
    val fnr: String,
)

data class Foedselsnummer(
    @get:JsonValue val asString: String,
)
