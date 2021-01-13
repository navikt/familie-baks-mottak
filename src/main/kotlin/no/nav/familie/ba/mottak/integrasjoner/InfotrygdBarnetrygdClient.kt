package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.integrasjoner.Sakspart.*
import no.nav.familie.http.client.AbstractRestClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.net.URI
import java.time.LocalDate

@Component
class InfotrygdBarnetrygdClient(@Value("\${FAMILIE_BA_INFOTRYGD_BARNETRYGD_API_URL}/infotrygd/barnetrygd")
                                private val clientUri: URI,
                                @Qualifier("clientCredentials") restOperations: RestOperations,
                                private val environment: Environment)
    : AbstractRestClient(restOperations, "infotrygd_barnetrygd_replika") {

    fun hentLøpendeUtbetalinger(søkersIdenter: List<String>, barnasIdenter: List<String>): InfotrygdSøkResponse<StønadDto> {
        return infotrygdResponseFra(
                request = { postForEntity(uri("stonad"), InfotrygdSøkRequest(søkersIdenter, barnasIdenter)) },
                onFailure = { ex -> IntegrasjonException("Feil ved søk etter stønad i infotrygd.", ex, uri("stonad")) }
        )
    }

    fun hentSaker(søkersIdenter: List<String>, barnasIdenter: List<String>): InfotrygdSøkResponse<SakDto> {
        return infotrygdResponseFra(
                request = { postForEntity(uri("saker"), InfotrygdSøkRequest(søkersIdenter, barnasIdenter)) },
                onFailure = { ex -> IntegrasjonException("Feil ved uthenting av saker fra infotrygd.", ex, uri("saker")) }
        )
    }

    private fun <T> infotrygdResponseFra(request: () -> InfotrygdSøkResponse<T>,
                                         onFailure: (Throwable) -> RuntimeException): InfotrygdSøkResponse<T> {
        return if (environment.activeProfiles.contains("e2e"))  InfotrygdSøkResponse(emptyList(), emptyList())
        else runCatching(request).getOrElse { throw onFailure(it) }
    }

    private fun uri(endepunkt: String) = URI.create("$clientUri/$endepunkt")
}

data class InfotrygdSøkRequest(val brukere: List<String>,
                               val barn: List<String>? = null)

data class InfotrygdSøkResponse<T> (
        val bruker: List<T>,
        val barn: List<T>,
)

data class StønadDto(
        val stønadId: Long,
        val sakNr: String? = null,
        val opphørtFom: String? = null,
        val opphørsgrunn: String? = null,
)

data class SakDto(
        val saksnr: String? = null,
        val kapittelnr: String? = null,
        val valg: String? = null,
        val type: String? = null,
        val årsakskode: String? = null,
        val vedtak: StønadDto? = null,
        val vedtaksdato: LocalDate? = null,
        val mottattdato: LocalDate? = null,
        val regDato: LocalDate? = null,
        val regAvEnhet: String? = null,
        val behenEnhet: String? = null,
        val status: String,         // S15_STATUS
)

enum class Opphørsgrunn(val kode: String) {
    MIGRERT("5")
}

enum class StatusKode(val beskrivelse: String) {
    IP("Saksbehandlingen kan starte med Statuskode IP (Ikke påbegynt). Da er det kun registrert en sakslinje uten at vedtaksbehandling er startet."),
    UB("Saksbehandling startet - når sak med status UB - Under Behandling - lagres, rapporteres hendelsen BehandlingOpprettet"),
    SG("Saksbehandler 1 har fullført og sendt til saksbehandler 2 for godkjenning"),
    UK("Underkjent av saksbehandler 2 med retur til saksbehandler 1"),
    FB("FerdigBehandlet"),
    FI("ferdig iverksatt"),
    RF("returnert feilsendt"),
    RM("returnert midlertidig"),
    RT("returnert til"),
    ST("sendt til"),
    VD("videresendt Direktoratet"),
    VI("venter på iverksetting"),
    VT("videresendt Trygderetten"),
}