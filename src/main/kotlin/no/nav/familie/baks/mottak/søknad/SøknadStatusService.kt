package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.task.JournalførKontantstøtteSøknadTask
import no.nav.familie.baks.mottak.task.JournalførSøknadTask
import no.nav.familie.leader.LeaderClient
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class SøknadStatusService(
    private val taskService: TaskService,
) {
    @Scheduled(cron = "0 0/30 * * * ?")
    private fun loggHvisLiteAktivitetForBarnetrygdOgKontantstøtte() {
        val isLeader = LeaderClient.isLeader()
        if (isLeader == null || !isLeader) {
            return
        }

        loggHvisLiteAktivitet(Søknadstype.BARNETRYGD)
        loggHvisLiteAktivitet(Søknadstype.KONTANTSTØTTE)
    }

    fun statusBarnetrygd(): StatusDto {
        logger.info("Sjekker status på barnetrygd søknad.")
        return lagStatusDto(Søknadstype.BARNETRYGD)
    }

    fun statusKontantstøtte(): StatusDto {
        logger.info("Sjekker status på kontantstøtte søknad.")
        return lagStatusDto(Søknadstype.KONTANTSTØTTE)
    }

    fun finnTidspunktForSisteMottatteSøknad(søknadstype: Søknadstype): LocalDateTime? =
        taskService
            .finnTasksMedStatus(
                status = Status.entries,
                type = søknadstype.journalføringTasktype(),
                page = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "opprettetTid")),
            ).firstOrNull()
            ?.opprettetTid

    private fun finnTidSidenSisteSøknad(søknadstype: Søknadstype): Duration? =
        finnTidspunktForSisteMottatteSøknad(søknadstype)
            ?.let { Duration.between(it, LocalDateTime.now()) }

    private fun loggHvisLiteAktivitet(søknadstype: Søknadstype) {
        if (erDagtid() && !erHelg()) {
            val tidSidenSisteSøknad = finnTidSidenSisteSøknad(søknadstype)
            when {
                tidSidenSisteSøknad == null -> logger.error("Status baks-mottak: Fant ingen mottatte søknader om ${søknadstype.name.lowercase()}")
                tidSidenSisteSøknad.toHours() >= 3 && LocalTime.now().isAfter(LocalTime.of(10, 30)) -> logger.error("Status baks-mottak: Det er ${tidSidenSisteSøknad.toHours()} timer siden vi sist mottok en søknad om ${søknadstype.name.lowercase()}")
                else -> logger.info("Status baks-mottak: Det er ${tidSidenSisteSøknad.toMinutes()} minutter siden vi sist mottok en søknad om ${søknadstype.name.lowercase()}")
            }
        }
    }

    private fun lagStatusDto(søknadstype: Søknadstype): StatusDto {
        val tidSidenSisteSøknad =
            finnTidSidenSisteSøknad(søknadstype)
                ?: return StatusDto(
                    status = Plattformstatus.DOWN,
                    description = "Fant ingen mottatte søknader om ${søknadstype.name.lowercase()}",
                )

        return when {
            erTidspunktMedForventetAktivitet() -> lagDagStatus(tidSidenSisteSøknad, søknadstype)
            else -> lagNattStatus(tidSidenSisteSøknad, søknadstype)
        }
    }

    private fun lagDagStatus(
        tidSidenSisteSøknad: Duration,
        søknadstype: Søknadstype,
    ) = when {
        tidSidenSisteSøknad.toHours() >= 12 -> {
            StatusDto(
                status = Plattformstatus.DOWN,
                description = "Det er over 12 timer siden sist vi mottok en søknad om ${søknadstype.name.lowercase()}",
            )
        }

        else -> {
            StatusDto(status = Plattformstatus.OK, description = "Alt er OK", logLink = null)
        }
    }

    private fun lagNattStatus(
        tidSidenSisteSøknad: Duration,
        søknadstype: Søknadstype,
    ) = when {
        tidSidenSisteSøknad.toHours() >= 24 -> {
            StatusDto(
                status = Plattformstatus.DOWN,
                description = "Det er over 24 timer siden sist vi mottok en søknad om ${søknadstype.name.lowercase()}",
            )
        }

        tidSidenSisteSøknad.toHours() >= 12 -> {
            StatusDto(
                status = Plattformstatus.ISSUE,
                description = "Det er over 12 timer siden sist vi mottok en søknad om ${søknadstype.name.lowercase()}",
            )
        }

        else -> {
            StatusDto(status = Plattformstatus.OK, description = "Alt er OK", logLink = null)
        }
    }

    private fun erHelg() = LocalDateTime.now().dayOfWeek.value in 6..7

    private fun erDagtid() = LocalDateTime.now().hour in 9..22

    private fun erTidspunktMedForventetAktivitet() = LocalDateTime.now().hour in 9..22

    private fun Søknadstype.journalføringTasktype() =
        when (this) {
            Søknadstype.BARNETRYGD -> JournalførSøknadTask.JOURNALFØR_SØKNAD
            Søknadstype.KONTANTSTØTTE -> JournalførKontantstøtteSøknadTask.JOURNALFØR_KONTANTSTØTTE_SØKNAD
        }

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
