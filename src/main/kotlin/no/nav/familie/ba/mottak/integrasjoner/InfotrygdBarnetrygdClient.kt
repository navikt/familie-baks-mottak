package no.nav.familie.ba.mottak.integrasjoner

import io.swagger.annotations.ApiModelProperty
import no.nav.commons.foedselsnummer.FoedselsNr
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
class InfotrygdBarnetrygdClient(@Value("\${FAMILIE_BA_INFOTRYGD_BARNETRYGD_API_URL}") private val clientUri: URI,
                                @Qualifier("jwtBearer") restOperations: RestOperations,
                                private val environment: Environment)
    : AbstractRestClient(restOperations, "infotrygd_barnetrygd") {

    fun hentSaker(søkersIdenter: List<String>, barnasIdenter: List<String> = emptyList()): SakResult {
        if (environment.activeProfiles.contains("e2e")) {
            return SakResult(emptyList(), emptyList())
        }

        val uri = URI.create("$clientUri/infotrygd/barnetrygd/saker")

        val request = InfotrygdSøkRequest(søkersIdenter.map { FoedselsNr(it) }, barnasIdenter.map { FoedselsNr(it) })

        return try {
            postForEntity(uri, request)
        } catch (ex: Exception) {
            throw IntegrasjonException(msg = "Feil ved uthenting av saker fra infotrygd.", ex, uri, søkersIdenter.firstOrNull())
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }
}

data class InfotrygdSøkRequest(val brukere: List<FoedselsNr>,
                               val barn: List<FoedselsNr>? = null)

data class SakResult(
        val bruker: List<SakDto>,
        val barn: List<SakDto>,
)

data class SakDto(
        val saksnr: String? = null,
        val regDato: LocalDate? = null,
        val mottattdato: LocalDate? = null,
        val kapittelnr: String? = null,
        val valg: String? = null,
        val Type: String? = null,
        val Aarsakskode: String? = null,
        val BehenEnhet: String? = null,
        val RegAvEnhet: String? = null,

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