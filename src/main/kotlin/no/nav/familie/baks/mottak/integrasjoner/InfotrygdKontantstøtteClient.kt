package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import java.net.URI
import java.time.YearMonth

@Service
class InfotrygdKontantstøtteClient(
    @Qualifier("clientCredentials") restOperations: RestOperations,
    @Value("FAMILIE_KS_INFOTRYGD_API_URL") private val clientUri: URI,
    private val environment: Environment
) :
    AbstractRestClient(restOperations, "familie-ks-infotrygd") {
    fun harKontantstøtteIInfotrygd(
        barnasIdenter: List<String>
    ): Boolean {
        return infotrygdResponseFra(
            request = postForEntity(uri("harLøpendeKontantstotteIInfotrygd"), InnsynRequest(barnasIdenter)),
            onFailure = { ex ->
                IntegrasjonException(
                    "Feil ved søk etter stønad i infotrygd.",
                    ex,
                    uri("harLøpendeKontantstotteIInfotrygd")
                )
            },
            e2eResponse = false
        )
    }

    fun hentPerioderMedKontantstøtteIInfotrygd(
        barnasIdenter: List<String>
    ): InnsynResponse {
        return infotrygdResponseFra(
            request = postForEntity(uri("hentPerioderMedKontantstøtteIInfotrygd"), InnsynRequest(barnasIdenter)),
            onFailure = { ex ->
                IntegrasjonException(
                    "Feil ved uthenting av saker fra infotrygd.",
                    ex,
                    uri("hentPerioderMedKontantstøtteIInfotrygd")
                )
            },
            e2eResponse = InnsynResponse(emptyList())
        )
    }

    private fun <T> infotrygdResponseFra(
        request: () -> T,
        onFailure: (Throwable) -> RuntimeException,
        e2eResponse: T
    ): T = if (environment.activeProfiles.contains("e2e")) {
        e2eResponse
    } else {
        runCatching(request).getOrElse { throw onFailure(it) }
    }

    private fun uri(endepunkt: String) = URI.create("$clientUri/$endepunkt")
}

data class InnsynRequest(
    val barn: List<String>
)

data class InnsynResponse(
    val data: List<StonadDto>
)

data class StonadDto(
    val fnr: Foedselsnummer,
    val fom: YearMonth?,
    val tom: YearMonth?,
    val belop: Int?,
    val barn: List<BarnDto>
)

data class BarnDto(
    val fnr: Foedselsnummer
)

data class Foedselsnummer(val asString: String)
