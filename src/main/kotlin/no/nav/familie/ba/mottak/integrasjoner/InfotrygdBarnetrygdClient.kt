package no.nav.familie.ba.mottak.integrasjoner

import io.swagger.annotations.ApiModelProperty
import no.nav.commons.foedselsnummer.FoedselsNr
import no.nav.familie.ba.mottak.integrasjoner.Sakspart.*
import no.nav.familie.http.client.AbstractRestClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

    fun hentLøpendeUtbetalinger(søkersIdenter: List<String>, barnasIdenter: List<String> = emptyList()): InfotrygdSøkResult<StønadDto> {
        return infotrygdSøkResult(URI.create("$clientUri/stonad"), søkersIdenter, barnasIdenter,
                                  meldingVedFeil = "Feil ved søk etter stønad i infotrygd.")
    }

    fun hentSaker(søkersIdenter: List<String>, barnasIdenter: List<String> = emptyList()): InfotrygdSøkResult<SakDto> {
        return infotrygdSøkResult(URI.create("$clientUri/stonad"), søkersIdenter, barnasIdenter,
                                  meldingVedFeil = "Feil ved uthenting av saker fra infotrygd.")
    }

    private fun <T> infotrygdSøkResult(uri: URI,
                                       søkersIdenter: List<String>,
                                       barnasIdenter: List<String>,
                                       meldingVedFeil: String): InfotrygdSøkResult<T> {

        if (environment.activeProfiles.contains("e2e")) {
            return InfotrygdSøkResult(emptyList(), emptyList())
        }

        return try {
            postForEntity(uri, InfotrygdSøkRequest(søkersIdenter.map { FoedselsNr(it) }, barnasIdenter.map { FoedselsNr(it) }))
        } catch (ex: Exception) {
            throw IntegrasjonException(msg = meldingVedFeil, ex, uri, søkersIdenter.firstOrNull())
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }
}

data class InfotrygdSøkRequest(val brukere: List<FoedselsNr>,
                               val barn: List<FoedselsNr>? = null)

data class InfotrygdSøkResult<T> (
        val bruker: List<T>,
        val barn: List<T>,
)


val InfotrygdSøkResult<StønadDto>.part: Sakspart?
    get() = if (bruker.isNotEmpty()) SØKER else if (barn.isNotEmpty()) ANNEN else null

val InfotrygdSøkResult<SakDto>.part: Sakspart? @JvmName("getSakspart")
    get() = if (bruker.harSak()) SØKER else if (barn.harSak()) ANNEN else null

private fun List<SakDto>.harSak(): Boolean {
    val (sakerMedVedtak, sakerUtenVedtak) = this.partition { it.vedtak != null }

    return sakerMedVedtak.let { it.all { it.vedtak!!.opphørsgrunn != "M" } && it.isNotEmpty() }
           || sakerUtenVedtak.any { it.status != "FB" }
}

data class StønadDto(
        val stønadId: Long,
        val sakNr: String,
        val opphørtFom: String?,
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