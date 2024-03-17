package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
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
class StatusController(val barnetrygdSøknadRepository: SøknadRepository) {

    val logger: Logger = LoggerFactory.getLogger(javaClass)
    @GetMapping()
    @Unprotected
    fun status(): StatusDto {
        val sistBarnetrygdSøknad = barnetrygdSøknadRepository.finnSisteLagredeSøknad()
        val tidSidenSisteBarnetrygdSøknad = Duration.between(LocalDateTime.now(), sistBarnetrygdSøknad.opprettetTid)
        loggHvisLiteAktivitet(tidSidenSisteBarnetrygdSøknad)
        return lagStatusDto(tidSidenSisteBarnetrygdSøknad)
    }

    private fun loggHvisLiteAktivitet(tidSidenSisteLagredeSøknad: Duration) {
        if (erDagtid() && !erHelg()) {
            when {
                tidSidenSisteLagredeSøknad.toHours() > 3 -> logger.error("Status baks-mottak: Det er ${tidSidenSisteLagredeSøknad.toHours()} timer siden vi sist mottok en søknad")
                tidSidenSisteLagredeSøknad.toMinutes() > 20 -> logger.error("Status baks-mottak: Det er ${tidSidenSisteLagredeSøknad.toMinutes()} minutter siden vi sist mottok en søknad")
            }
        }
    }

    private fun lagStatusDto(tidSidenSisteLagredeSøknad: Duration) = when {
        erTidspunktMedForventetAktivitet() -> lagDagStatus(tidSidenSisteLagredeSøknad)
        else -> lagNattStatus(tidSidenSisteLagredeSøknad)
    }

    private fun lagDagStatus(tidSidenSisteLagredeSøknad: Duration) =
        when {
            tidSidenSisteLagredeSøknad.toHours() > 12 -> StatusDto(
                status = Plattformstatus.DOWN,
                description = "Det er over 12 timer siden sist vi mottok en søknad",
            )
            else -> StatusDto(status = Plattformstatus.OK, description = "Alt er OK", logLink = null)
        }

    private fun lagNattStatus(tidSidenSisteLagredeSøknad: Duration) =
        when {
            tidSidenSisteLagredeSøknad.toHours() > 12 -> StatusDto(
                status = Plattformstatus.ISSUE,
                description = "Det er over 12 timer siden sist vi mottok en søknad",
            )
            tidSidenSisteLagredeSøknad.toHours() > 24 -> StatusDto(
                status = Plattformstatus.DOWN,
                description = "Det er over 24 timer siden sist vi mottok en søknad",
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
    OK, ISSUE, DOWN
}
