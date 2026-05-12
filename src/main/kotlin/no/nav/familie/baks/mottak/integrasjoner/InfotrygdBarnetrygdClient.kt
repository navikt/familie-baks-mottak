package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.texas.TexasRestClientFactory
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkRequest
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.body
import java.net.URI
import no.nav.familie.kontrakter.ba.infotrygd.Sak as SakDto
import no.nav.familie.kontrakter.ba.infotrygd.Stønad as StønadDto

@Component
class InfotrygdBarnetrygdClient(
    @Value("\${FAMILIE_BA_INFOTRYGD_API_URL}/infotrygd/barnetrygd") private val clientUri: URI,
    @Value("\${FAMILIE_BA_INFOTRYGD_SCOPE}") private val infotrygdScope: String,
    texasRestClientFactory: TexasRestClientFactory,
) {
    private val restClient = texasRestClientFactory.lagMaskinRestKlient(infotrygdScope)

    fun hentLøpendeUtbetalinger(
        søkersIdenter: List<String>,
        barnasIdenter: List<String>,
    ): InfotrygdSøkResponse<StønadDto> =
        infotrygdResponseFra(
            request = {
                restClient
                    .post()
                    .uri(uri("stonad"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(InfotrygdSøkRequest(søkersIdenter, barnasIdenter))
                    .retrieve()
                    .body<InfotrygdSøkResponse<StønadDto>>()!!
            },
            onFailure = { ex -> IntegrasjonException("Feil ved søk etter stønad i infotrygd.", ex, uri("stonad")) },
        )

    fun hentSaker(
        søkersIdenter: List<String>,
        barnasIdenter: List<String>,
    ): InfotrygdSøkResponse<SakDto> =
        infotrygdResponseFra(
            request = {
                restClient
                    .post()
                    .uri(uri("saker"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(InfotrygdSøkRequest(søkersIdenter, barnasIdenter))
                    .retrieve()
                    .body<InfotrygdSøkResponse<SakDto>>()!!
            },
            onFailure = { ex -> IntegrasjonException("Feil ved uthenting av saker fra infotrygd.", ex, uri("saker")) },
        )

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

enum class Opphørsgrunn(
    val kode: String,
) {
    MIGRERT("5"),
}

enum class StatusKode(
    val beskrivelse: String,
) {
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
