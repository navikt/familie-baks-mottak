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
    @GetMapping()
    @Unprotected
    fun status(): StatusDto {
        val sistBarnetrygdSøknad = barnetrygdSøknadRepository.finnSisteLagredeSøknad()
        val sistKontantstøtteSøknad = kontantstøtteSøknadRepository.finnSisteLagredeSøknad()
        val tidSidenSisteBarnetrygdSøknad = Duration.between(LocalDateTime.now(), sistBarnetrygdSøknad.opprettetTid)
        val tidSidenSisteKontantstøtteSøknad = Duration.between(LocalDateTime.now(), sistKontantstøtteSøknad.opprettetTid)
        loggHvisLiteAktivitet(tidSidenSisteBarnetrygdSøknad, tidSidenSisteKontantstøtteSøknad)
        return lagStatusDto(tidSidenSisteBarnetrygdSøknad, tidSidenSisteKontantstøtteSøknad)
    }

    private fun loggHvisLiteAktivitet(tidSidenSisteBASøknad: Duration, tidSidenSisteKSSøknad: Duration) {
        if (erDagtid() && !erHelg()) {
            when {
                tidSidenSisteBASøknad.toHours() > 3 -> logger.error("Status baks-mottak: Det er ${tidSidenSisteBASøknad.toHours()} timer siden vi sist mottok en barnetrygdsøknad")
                tidSidenSisteBASøknad.toMinutes() > 20 -> logger.warn("Status baks-mottak: Det er ${tidSidenSisteBASøknad.toMinutes()} minutter siden vi sist mottok en barnetrygdsøknad")
            }
            when {
                tidSidenSisteKSSøknad.toHours() > 3 -> logger.error("Status baks-mottak: Det er ${tidSidenSisteKSSøknad.toHours()} timer siden vi sist mottok en kontantstøttesøknad")
                tidSidenSisteKSSøknad.toMinutes() > 20 -> logger.warn("Status baks-mottak: Det er ${tidSidenSisteKSSøknad.toMinutes()} minutter siden vi sist mottok en kontantstøttesøknad")
            }
        }
    }

    private fun lagStatusDto(tidSidenSisteBASøknad: Duration, tidSidenSisteKSSøknad: Duration) = when {
        erTidspunktMedForventetAktivitet() -> lagDagStatus(tidSidenSisteBASøknad, tidSidenSisteKSSøknad)
        else -> lagNattStatus(tidSidenSisteBASøknad, tidSidenSisteKSSøknad)
    }

    private fun lagDagStatus(tidSidenSisteBASøknad: Duration, tidSidenSisteKSSøknad: Duration) =
        when {
            tidSidenSisteBASøknad.toHours() > 12 && tidSidenSisteKSSøknad.toHours() > 12 -> StatusDto(
                status = Plattformstatus.DOWN,
                description = "Det er over 12 timer siden sist vi mottok en søknad om barnetrygd eller kontantstøtte",
            )
            tidSidenSisteBASøknad.toHours() > 12 -> StatusDto(
                status = Plattformstatus.DOWN,
                description = "Det er over 12 timer siden sist vi mottok en søknad om barnetrygd",
            )
            tidSidenSisteKSSøknad.toHours() > 12 -> StatusDto(
                status = Plattformstatus.DOWN,
                description = "Det er over 12 timer siden sist vi mottok en søknad om kontantstøtte",
            )
            else -> StatusDto(status = Plattformstatus.OK, description = "Alt er OK", logLink = null)
        }

    private fun lagNattStatus(tidSidenSisteBASøknad: Duration, tidSidenSisteKSSøknad: Duration) =
        when {
            tidSidenSisteBASøknad.toHours() > 24 && tidSidenSisteKSSøknad.toHours() > 24 -> StatusDto(
                status = Plattformstatus.DOWN,
                description = "Det er over 24 timer siden sist vi mottok en søknad om barnetrygd eller kontantstøtte",
            )
            tidSidenSisteBASøknad.toHours() > 24 -> StatusDto(
                status = Plattformstatus.DOWN,
                description = "Det er over 24 timer siden sist vi mottok en søknad om barnetrygd",
            )
            tidSidenSisteKSSøknad.toHours() > 24 -> StatusDto(
                status = Plattformstatus.DOWN,
                description = "Det er over 24 timer siden sist vi mottok en søknad om kontantstøtte",
            )
            tidSidenSisteBASøknad.toHours() > 12 && tidSidenSisteKSSøknad.toHours() > 12 -> StatusDto(
                status = Plattformstatus.ISSUE,
                description = "Det er over 12 timer siden sist vi mottok en søknad om barnetrygd eller kontantstøtte",
            )
            tidSidenSisteBASøknad.toHours() > 12 -> StatusDto(
                status = Plattformstatus.ISSUE,
                description = "Det er over 12 timer siden sist vi mottok en søknad om barnetrygd",
            )
            tidSidenSisteKSSøknad.toHours() > 12 -> StatusDto(
                status = Plattformstatus.ISSUE,
                description = "Det er over 12 timer siden sist vi mottok en søknad om kontantstøtte",
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
