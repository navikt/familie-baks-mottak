package no.nav.familie.ba.mottak.integrasjoner

import io.swagger.annotations.ApiModelProperty
import no.nav.commons.foedselsnummer.FoedselsNr
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
                                @Qualifier("jwtBearer") restOperations: RestOperations,
                                private val environment: Environment)
    : AbstractRestClient(restOperations, "infotrygd_barnetrygd_replika") {

    fun hentLøpendeUtbetalinger(søkersIdenter: List<String>, barnasIdenter: List<String>): InfotrygdSøkResponse<StønadDto> {
        return infotrygdResponseFra(
                postRequest = { postForEntity(uri("stonad"), mapTilInfotrygdSøkRequest(søkersIdenter, barnasIdenter)) },
                onFailure = { ex -> IntegrasjonException("Feil ved søk etter stønad i infotrygd.", ex, uri("stonad")) }
        )
    }

    fun hentSaker(søkersIdenter: List<String>, barnasIdenter: List<String>): InfotrygdSøkResponse<SakDto> {
        return infotrygdResponseFra(
                postRequest = { postForEntity(uri("saker"), mapTilInfotrygdSøkRequest(søkersIdenter, barnasIdenter)) },
                onFailure = { ex -> IntegrasjonException("Feil ved uthenting av saker fra infotrygd.", ex, uri("saker")) }
        )
    }

    private fun <T> infotrygdResponseFra(postRequest: () -> InfotrygdSøkResponse<T>,
                                         onFailure: (Throwable) -> RuntimeException): InfotrygdSøkResponse<T> {
        return if (environment.activeProfiles.contains("e2e"))  InfotrygdSøkResponse(emptyList(), emptyList())
        else runCatching(postRequest).getOrElse { throw onFailure(it) }
    }

    private fun uri(endepunkt: String) = URI.create("$clientUri/$endepunkt")

    private fun mapTilInfotrygdSøkRequest(søkersIdenter: List<String>,
                                          barnasIdenter: List<String>): InfotrygdSøkRequest {
        return InfotrygdSøkRequest(søkersIdenter.map { FoedselsNr(it) }, barnasIdenter.map { FoedselsNr(it) })
    }
}

data class InfotrygdSøkRequest(val brukere: List<FoedselsNr>,
                               val barn: List<FoedselsNr>? = null)

data class InfotrygdSøkResponse<T> (
        val bruker: List<T>,
        val barn: List<T>,
)


val InfotrygdSøkResponse<StønadDto>.resultat: Sakspart?
    get() = if (bruker.isNotEmpty()) SØKER else if (barn.isNotEmpty()) ANNEN else null

val InfotrygdSøkResponse<SakDto>.resultat: Sakspart? @JvmName("getSakspart")
    get() = if (bruker.harSak()) SØKER else if (barn.harSak()) ANNEN else null

private fun List<SakDto>.harSak(): Boolean {
    val (sakerMedVedtak, sakerUtenVedtak) = this.partition { it.vedtak != null }

    return sakerMedVedtak.let { it.all { it.vedtak!!.opphørsgrunn != "M" } && it.isNotEmpty() }
           || sakerUtenVedtak.any { it.status != "FB" }
}

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
        val Type: String? = null,
        val Aarsakskode: String? = null,
        val vedtak: StønadDto? = null,
        val Vedtaksdato: LocalDate? = null,
        val mottattdato: LocalDate? = null,
        val regDato: LocalDate? = null,
        val RegAvEnhet: String? = null,
        val BehenEnhet: String? = null,


        @ApiModelProperty(notes = """
        IP: - Saksbehandlingen kan starte med Statuskode IP (Ikke påbegynt). Da er det kun registrert en sakslinje uten at vedtaksbehandling er startet.
        UB: - Saksbehandling startet - når sak med status UB - Under Behandling - lagres, rapporteres hendelsen BehandlingOpprettet
        SG: - Saksbehandler 1 har fullført og sendt til saksbehandler 2 for godkjenning
        UK: - Underkjent av saksbehandler 2 med retur til saksbehandler 1
        FB: - FerdigBehandlet
        FI: - ferdig iverksatt
        RF: - returnert feilsendt
        RM: - returnert midlertidig
        RT: - returnert til
        ST: - sendt til
        VD: - videresendt Direktoratet
        VI: - venter på iverksetting
        VT: - videresendt Trygderetten
        
        Kolonne: S15_STATUS.
    """,
                          allowableValues = "IP,UB,SG,UK,FB,FI,RF,RM,RT,ST,VD,VI,VT"
        )
        val status: String,         // S15_STATUS

)