package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkRequest
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkResponse
import no.nav.familie.kontrakter.ba.infotrygd.Stønad as StønadDto
import no.nav.familie.kontrakter.ba.infotrygd.Sak as SakDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import java.net.URI

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