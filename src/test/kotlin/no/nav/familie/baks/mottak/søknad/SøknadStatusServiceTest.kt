package no.nav.familie.baks.mottak.søknad

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import no.nav.familie.baks.mottak.task.JournalførKontantstøtteSøknadTask
import no.nav.familie.baks.mottak.task.JournalførSøknadTask
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Properties

class SøknadStatusServiceTest {
    private val taskService = mockk<TaskService>()
    private val søknadStatusService = SøknadStatusService(taskService)

    @BeforeEach
    fun beforeEach() {
        mockkStatic(LocalDateTime::class)
    }

    @AfterEach
    fun afterEach() {
        unmockkStatic(LocalDateTime::class)
    }

    @Test
    fun `skal gi status DOWN dersom det ikke har kommet inn noen søknader på 12 timer for kontantstøtte`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        mockSisteMottatteSøknad(Søknadstype.KONTANTSTØTTE, LocalDateTime.of(2024, 8, 1, 21, 59))

        // Act
        val statusKontantstøtte = søknadStatusService.statusKontantstøtte()

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.DOWN)
    }

    @Test
    fun `skal gi status OK dersom det har kommet inn noen søknader på 12 timer for kontantstøtte`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        mockSisteMottatteSøknad(Søknadstype.KONTANTSTØTTE, LocalDateTime.of(2024, 8, 2, 8, 59))

        // Act
        val statusKontantstøtte = søknadStatusService.statusKontantstøtte()

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.OK)
        assertThat(statusKontantstøtte.description).isEqualTo("Alt er OK")
        assertThat(statusKontantstøtte.logLink).isNull()
    }

    @Test
    fun `skal gi status DOWN dersom det ikke har kommet inn noen søknader på 12 timer for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        mockSisteMottatteSøknad(Søknadstype.BARNETRYGD, LocalDateTime.of(2024, 8, 1, 21, 59))

        // Act
        val statusBarnetrygd = søknadStatusService.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.DOWN)
    }

    @Test
    fun `skal gi status OK dersom det har kommet inn noen søknader på 12 timer for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        mockSisteMottatteSøknad(Søknadstype.BARNETRYGD, LocalDateTime.of(2024, 8, 2, 8, 59))

        // Act
        val statusBarnetrygd = søknadStatusService.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.OK)
        assertThat(statusBarnetrygd.description).isEqualTo("Alt er OK")
        assertThat(statusBarnetrygd.logLink).isNull()
    }

    @Test
    fun `skal gi status DOWN dersom det er natt og det ikke har kommet inn noen søknader på 24 timer for kontantstøtte`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 2, 0)
        mockSisteMottatteSøknad(Søknadstype.KONTANTSTØTTE, LocalDateTime.of(2024, 8, 1, 1, 59))

        // Act
        val statusKontantstøtte = søknadStatusService.statusKontantstøtte()

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.DOWN)
    }

    @Test
    fun `skal gi status OK dersom det er natt og det har kommet inn noen søknader på 12 timer for kontantstøtte`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 2, 0)
        mockSisteMottatteSøknad(Søknadstype.KONTANTSTØTTE, LocalDateTime.of(2024, 8, 1, 14, 59))

        // Act
        val statusKontantstøtte = søknadStatusService.statusKontantstøtte()

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.OK)
        assertThat(statusKontantstøtte.description).isEqualTo("Alt er OK")
        assertThat(statusKontantstøtte.logLink).isNull()
    }

    @Test
    fun `skal gi status DOWN dersom det er natt og det ikke har kommet inn noen søknader på 24 timer for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 2, 0)
        mockSisteMottatteSøknad(Søknadstype.BARNETRYGD, LocalDateTime.of(2024, 8, 1, 1, 59))

        // Act
        val statusBarnetrygd = søknadStatusService.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.DOWN)
    }

    @Test
    fun `skal gi status OK dersom det er natt og det ikke har kommet inn noen søknader på 24 timer for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 2, 0)
        mockSisteMottatteSøknad(Søknadstype.BARNETRYGD, LocalDateTime.of(2024, 8, 1, 14, 59))

        // Act
        val statusBarnetrygd = søknadStatusService.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.OK)
        assertThat(statusBarnetrygd.description).isEqualTo("Alt er OK")
        assertThat(statusBarnetrygd.logLink).isNull()
    }

    @Test
    fun `skal gi status ISSUE dersom det er natt og det ikke har kommet inn noen søknader på 12 timer for kontantstøtte`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 2, 0)
        mockSisteMottatteSøknad(Søknadstype.KONTANTSTØTTE, LocalDateTime.of(2024, 8, 1, 13, 59))

        // Act
        val statusKontantstøtte = søknadStatusService.statusKontantstøtte()

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.ISSUE)
    }

    @Test
    fun `skal gi status ISSUE dersom det er natt og det ikke har kommet inn noen søknader på 12 timer for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 2, 0)
        mockSisteMottatteSøknad(Søknadstype.BARNETRYGD, LocalDateTime.of(2024, 8, 1, 13, 59))

        // Act
        val statusBarnetrygd = søknadStatusService.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.ISSUE)
    }

    @Test
    fun `skal gi status DOWN dersom det ikke finnes noen mottatte søknader for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        mockSisteMottatteSøknad(Søknadstype.BARNETRYGD, null)

        // Act
        val statusBarnetrygd = søknadStatusService.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.DOWN)
        assertThat(statusBarnetrygd.description).isEqualTo("Fant ingen mottatte søknader om barnetrygd")
    }

    private fun mockSisteMottatteSøknad(
        søknadstype: Søknadstype,
        opprettetTid: LocalDateTime?,
    ) {
        val tasktype =
            when (søknadstype) {
                Søknadstype.BARNETRYGD -> JournalførSøknadTask.JOURNALFØR_SØKNAD
                Søknadstype.KONTANTSTØTTE -> JournalførKontantstøtteSøknadTask.JOURNALFØR_KONTANTSTØTTE_SØKNAD
            }
        every { taskService.finnTasksMedStatus(any(), tasktype, any()) } returns
            listOfNotNull(
                opprettetTid?.let { Task(type = tasktype, payload = "1", properties = Properties()).copy(opprettetTid = it) },
            )
    }
}
