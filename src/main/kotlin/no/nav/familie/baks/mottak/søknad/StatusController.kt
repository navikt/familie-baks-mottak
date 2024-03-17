package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadRepository
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.LocalDateTime

@RestController
@RequestMapping(path = ["/api/status"], produces = [MediaType.APPLICATION_JSON_VALUE])
class StatusController(val barnetrygdSøknadRepository: SøknadRepository, val kontantstøtteSøknadRepository: KontantstøtteSøknadRepository) {
    val logger: Logger = LoggerFactory.getLogger(javaClass)

    @GetMapping(value = ["/barnetrygd"])
    @Unprotected
    fun statusBarnetrygd(): StatusDto {
        val sistBarnetrygdSøknad = barnetrygdSøknadRepository.finnSisteLagredeSøknad()
        val tidSidenSisteBarnetrygdSøknad = Duration.between(LocalDateTime.now(), sistBarnetrygdSøknad.opprettetTid)
        loggHvisLiteAktivitet(tidSidenSisteBarnetrygdSøknad, Søknadstype.BARNETRYGD)
        return lagStatusDto(tidSidenSisteBarnetrygdSøknad, Søknadstype.BARNETRYGD)
    }

    @GetMapping(value = ["/kontantstotte"])
    @Unprotected
    fun statusKontantstøtte(): StatusDto {
        val sistKontantstøtteSøknad = kontantstøtteSøknadRepository.finnSisteLagredeSøknad()
        val tidSidenSisteKontantstøtteSøknad = Duration.between(LocalDateTime.now(), sistKontantstøtteSøknad.opprettetTid)
        loggHvisLiteAktivitet(tidSidenSisteKontantstøtteSøknad, Søknadstype.KONTANTSTØTTE)
        return lagStatusDto(tidSidenSisteKontantstøtteSøknad, Søknadstype.KONTANTSTØTTE)
    }

    private fun loggHvisLiteAktivitet(
        tidSidenSisteSøknad: Duration,
        søknadstype: Søknadstype,
    ) {
        if (erDagtid() && !erHelg()) {
            when {
                tidSidenSisteSøknad.toHours() > 3 -> logger.error("Status baks-mottak: Det er ${tidSidenSisteSøknad.toHours()} timer siden vi sist mottok en søknad om ${søknadstype.name.lowercase()}")
                tidSidenSisteSøknad.toMinutes() > 20 -> logger.warn("Status baks-mottak: Det er ${tidSidenSisteSøknad.toMinutes()} minutter siden vi sist mottok en søknad om ${søknadstype.name.lowercase()}")
            }
        }
    }

    private fun lagStatusDto(
        tidSidenSisteSøknad: Duration,
        søknadstype: Søknadstype,
    ) =
        when {
            erTidspunktMedForventetAktivitet() -> lagDagStatus(tidSidenSisteSøknad, søknadstype)
            else -> lagNattStatus(tidSidenSisteSøknad, søknadstype)
        }

    private fun lagDagStatus(
        tidSidenSisteSøknad: Duration,
        søknadstype: Søknadstype,
    ) =
        when {
            tidSidenSisteSøknad.toHours() > 12 ->
                StatusDto(
                    status = Plattformstatus.DOWN,
                    description = "Det er over 12 timer siden sist vi mottok en søknad om ${søknadstype.name.lowercase()}",
                )
            else -> StatusDto(status = Plattformstatus.OK, description = "Alt er OK", logLink = null)
        }

    private fun lagNattStatus(
        tidSidenSisteSøknad: Duration,
        søknadstype: Søknadstype,
    ) =
        when {
            tidSidenSisteSøknad.toHours() > 24 ->
                StatusDto(
                    status = Plattformstatus.DOWN,
                    description = "Det er over 24 timer siden sist vi mottok en søknad om ${søknadstype.name.lowercase()}",
                )
            tidSidenSisteSøknad.toHours() > 12 ->
                StatusDto(
                    status = Plattformstatus.ISSUE,
                    description = "Det er over 12 timer siden sist vi mottok en søknad om ${søknadstype.name.lowercase()}",
                )
            else -> StatusDto(status = Plattformstatus.OK, description = "Alt er OK", logLink = null)
        }

    private fun erHelg() = LocalDateTime.now().dayOfWeek.value in 6..7

    private fun erDagtid() = LocalDateTime.now().hour in 9..22

    private fun erTidspunktMedForventetAktivitet() = LocalDateTime.now().hour in 12..21
}

const val LOG_URL = "https://logs.adeo.no/app/r/s/OJZqp"

data class StatusDto(val status: Plattformstatus, val description: String? = null, val logLink: String? = LOG_URL)

enum class Plattformstatus {
    OK,
    ISSUE,
    DOWN,
}

enum class Søknadstype {
    KONTANTSTØTTE,
    BARNETRYGD,
}
