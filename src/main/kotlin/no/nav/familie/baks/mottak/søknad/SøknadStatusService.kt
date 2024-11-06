package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

@Service
class SøknadStatusService(
    val barnetrygdSøknadRepository: SøknadRepository,
    val kontantstøtteSøknadRepository: KontantstøtteSøknadRepository,
) {
    @Scheduled(cron = "0 0/30 * * * ?")
    private fun sjekkStatusForBarnetrygdOgKontantstøtte() {
        logger.info("Sjekker status for barnetrygd og kontantstøtte")
        statusBarnetrygd()
        statusKontantstøtte()
    }

    fun statusBarnetrygd(): StatusDto {
        logger.info("Sjekker status på barnetrygd søknad.")
        val sistBarnetrygdSøknad = barnetrygdSøknadRepository.finnSisteLagredeSøknad()
        val tidSidenSisteBarnetrygdSøknad = Duration.between(sistBarnetrygdSøknad.opprettetTid, LocalDateTime.now())
        loggHvisLiteAktivitet(tidSidenSisteBarnetrygdSøknad, Søknadstype.BARNETRYGD)
        return lagStatusDto(tidSidenSisteBarnetrygdSøknad, Søknadstype.BARNETRYGD)
    }

    fun statusKontantstøtte(): StatusDto {
        logger.info("Sjekker status på kontantstøtte søknad.")
        val sistKontantstøtteSøknad = kontantstøtteSøknadRepository.finnSisteLagredeSøknad()
        val tidSidenSisteKontantstøtteSøknad = Duration.between(sistKontantstøtteSøknad.opprettetTid, LocalDateTime.now())
        loggHvisLiteAktivitet(tidSidenSisteKontantstøtteSøknad, Søknadstype.KONTANTSTØTTE)
        return lagStatusDto(tidSidenSisteKontantstøtteSøknad, Søknadstype.KONTANTSTØTTE)
    }

    private fun loggHvisLiteAktivitet(
        tidSidenSisteSøknad: Duration,
        søknadstype: Søknadstype,
    ) {
        if (erDagtid() && !erHelg()) {
            when {
                tidSidenSisteSøknad.toHours() >= 3 -> logger.error("Status baks-mottak: Det er ${tidSidenSisteSøknad.toHours()} timer siden vi sist mottok en søknad om ${søknadstype.name.lowercase()}")
                tidSidenSisteSøknad.toMinutes() >= 20 -> logger.warn("Status baks-mottak: Det er ${tidSidenSisteSøknad.toMinutes()} minutter siden vi sist mottok en søknad om ${søknadstype.name.lowercase()}")
                else -> logger.info("Status baks-mottak: Det er ${tidSidenSisteSøknad.toMinutes()} minutter siden vi sist mottok en søknad om ${søknadstype.name.lowercase()}")
            }
        }
    }

    private fun lagStatusDto(
        tidSidenSisteSøknad: Duration,
        søknadstype: Søknadstype,
    ) = when {
        erTidspunktMedForventetAktivitet() -> lagDagStatus(tidSidenSisteSøknad, søknadstype)
        else -> lagNattStatus(tidSidenSisteSøknad, søknadstype)
    }

    private fun lagDagStatus(
        tidSidenSisteSøknad: Duration,
        søknadstype: Søknadstype,
    ) = when {
        tidSidenSisteSøknad.toHours() >= 12 ->
            StatusDto(
                status = Plattformstatus.DOWN,
                description = "Det er over 12 timer siden sist vi mottok en søknad om ${søknadstype.name.lowercase()}",
            )

        else -> StatusDto(status = Plattformstatus.OK, description = "Alt er OK", logLink = null)
    }

    private fun lagNattStatus(
        tidSidenSisteSøknad: Duration,
        søknadstype: Søknadstype,
    ) = when {
        tidSidenSisteSøknad.toHours() >= 24 ->
            StatusDto(
                status = Plattformstatus.DOWN,
                description = "Det er over 24 timer siden sist vi mottok en søknad om ${søknadstype.name.lowercase()}",
            )

        tidSidenSisteSøknad.toHours() >= 12 ->
            StatusDto(
                status = Plattformstatus.ISSUE,
                description = "Det er over 12 timer siden sist vi mottok en søknad om ${søknadstype.name.lowercase()}",
            )

        else -> StatusDto(status = Plattformstatus.OK, description = "Alt er OK", logLink = null)
    }

    private fun erHelg() = LocalDateTime.now().dayOfWeek.value in 6..7

    private fun erDagtid() = LocalDateTime.now().hour in 9..22

    private fun erTidspunktMedForventetAktivitet() = LocalDateTime.now().hour in 9..22

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SøknadStatusService::class.java)
    }
}

const val LOG_URL = "https://logs.adeo.no/app/r/s/OJZqp"

data class StatusDto(
    val status: Plattformstatus,
    val description: String? = null,
    val logLink: String? = LOG_URL,
)

enum class Plattformstatus {
    OK,
    ISSUE,
    DOWN,
}

enum class Søknadstype {
    KONTANTSTØTTE,
    BARNETRYGD,
}
