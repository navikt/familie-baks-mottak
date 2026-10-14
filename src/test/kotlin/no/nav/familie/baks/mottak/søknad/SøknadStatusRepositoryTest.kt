package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.DevLauncherPostgres
import no.nav.familie.baks.mottak.task.JournalførKontantstøtteSøknadTask
import no.nav.familie.baks.mottak.task.JournalførSøknadTask
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Properties
import java.util.UUID

@ExtendWith(SpringExtension::class)
@ActiveProfiles("postgres", "testcontainers")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class SøknadStatusRepositoryTest(
    @Autowired
    val søknadStatusRepository: SøknadStatusRepository,
    @Autowired
    val taskService: TaskService,
) {
    @Test
    fun `skal finne tidspunkt for siste journalføringstask av riktig type`() {
        // Arrange
        val nå = LocalDateTime.now().plusYears(10)
        lagreFerdigTask(JournalførSøknadTask.JOURNALFØR_SØKNAD, nå.minusHours(2))
        lagreFerdigTask(JournalførSøknadTask.JOURNALFØR_SØKNAD, nå.minusHours(1))
        lagreFerdigTask(JournalførKontantstøtteSøknadTask.JOURNALFØR_KONTANTSTØTTE_SØKNAD, nå.minusHours(3))
        lagreFerdigTask(JournalførSøknadTask.JOURNALFØR_SØKNAD, nå)

        // Act
        val sisteBarnetrygd = søknadStatusRepository.finnTidspunktForSisteMottatteSøknad(Søknadstype.BARNETRYGD)
        val sisteKontantstøtte = søknadStatusRepository.finnTidspunktForSisteMottatteSøknad(Søknadstype.KONTANTSTØTTE)

        // Assert
        assertThat(sisteBarnetrygd).isCloseTo(nå, within(1, ChronoUnit.MILLIS))
        assertThat(sisteKontantstøtte).isCloseTo(nå.minusHours(3), within(1, ChronoUnit.MILLIS))
    }

    private fun lagreFerdigTask(
        type: String,
        opprettetTid: LocalDateTime,
    ) {
        taskService.save(
            Task(type = type, payload = UUID.randomUUID().toString(), properties = Properties())
                .copy(status = Status.FERDIG, opprettetTid = opprettetTid),
        )
    }
}
