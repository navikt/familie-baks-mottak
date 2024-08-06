package no.nav.familie.baks.mottak.søknad

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class StatusControllerTest {
    private val søknadRepository = mockk<SøknadRepository>()
    private val kontantstøtteSøknadRepository = mockk<KontantstøtteSøknadRepository>()
    private val statusController = StatusController(søknadRepository, kontantstøtteSøknadRepository)
    private val logAppender = LogAppender()
    private val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger

    @BeforeEach
    fun beforeEach() {
        mockkStatic(LocalDateTime::class)
        logger.addAppender(logAppender)
        logAppender.start()
    }

    @AfterEach
    fun afterEach() {
        unmockkStatic(LocalDateTime::class)
    }

    @Test
    fun `skal gi status DOWN dersom det ikke har kommet inn noen søknader på 12 timer for kontantstøtte`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        every { kontantstøtteSøknadRepository.finnSisteLagredeSøknad() } returns
            DBKontantstøtteSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 1, 21, 59),
                fnr = "",
            )

        // Act
        val statusKontantstøtte = statusController.statusKontantstøtte()

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.DOWN)
        assertThat(logAppender.logEvents.filter { it.level == Level.ERROR }.size).isEqualTo(1)
    }

    @Test
    fun `skal gi status DOWN dersom det ikke har kommet inn noen søknader på 12 timer for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        every { søknadRepository.finnSisteLagredeSøknad() } returns
            DBBarnetrygdSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 1, 21, 59),
                fnr = "",
            )

        // Act
        val statusBarnetrygd = statusController.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.DOWN)
        assertThat(logAppender.logEvents.filter { it.level == Level.ERROR }.size).isEqualTo(1)
    }

    @Test
    fun `skal gi status DOWN dersom det er natt og det ikke har kommet inn noen søknader på 24 timer for kontantstøtte`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 2, 0)
        every { kontantstøtteSøknadRepository.finnSisteLagredeSøknad() } returns
            DBKontantstøtteSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 1, 1, 59),
                fnr = "",
            )

        // Act
        val statusKontantstøtte = statusController.statusKontantstøtte()

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.DOWN)
    }

    @Test
    fun `skal gi status DOWN dersom det er natt og det ikke har kommet inn noen søknader på 24 timer for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 2, 0)
        every { søknadRepository.finnSisteLagredeSøknad() } returns
            DBBarnetrygdSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 1, 1, 59),
                fnr = "",
            )

        // Act
        val statusBarnetrygd = statusController.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.DOWN)
    }

    @Test
    fun `skal gi status ISSUE dersom det er natt og det ikke har kommet inn noen søknader på 12 timer for kontantstøtte`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 2, 0)
        every { kontantstøtteSøknadRepository.finnSisteLagredeSøknad() } returns
            DBKontantstøtteSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 1, 13, 59),
                fnr = "",
            )

        // Act
        val statusKontantstøtte = statusController.statusKontantstøtte()

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.ISSUE)
    }

    @Test
    fun `skal gi status ISSUE dersom det er natt og det ikke har kommet inn noen søknader på 12 timer for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 2, 0)
        every { søknadRepository.finnSisteLagredeSøknad() } returns
            DBBarnetrygdSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 1, 13, 59),
                fnr = "",
            )

        // Act
        val statusBarnetrygd = statusController.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.ISSUE)
    }

    @Test
    fun `skal logge error dersom det er dagtid og ikke helg og mer enn 3 timer siden vi har mottatt en søknad for kontantstøtte`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        every { kontantstøtteSøknadRepository.finnSisteLagredeSøknad() } returns
            DBKontantstøtteSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 2, 6, 59),
                fnr = "",
            )

        // Act
        val statusKontantstøtte = statusController.statusKontantstøtte()

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.OK)
        assertThat(logAppender.logEvents.filter { it.level == Level.ERROR }.size).isEqualTo(1)
    }

    @Test
    fun `skal logge error dersom det er dagtid og ikke helg og mer enn 3 timer siden vi har mottatt en søknad for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        every { søknadRepository.finnSisteLagredeSøknad() } returns
            DBBarnetrygdSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 2, 6, 59),
                fnr = "",
            )

        // Act
        val statusBarnetrygd = statusController.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.OK)
        assertThat(logAppender.logEvents.filter { it.level == Level.ERROR }.size).isEqualTo(1)
    }

    @Test
    fun `skal logge warning dersom det er dagtid og ikke helg og mer enn 20 minutter siden vi har mottatt en søknad for kontantstøtte`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        every { kontantstøtteSøknadRepository.finnSisteLagredeSøknad() } returns
            DBKontantstøtteSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 2, 9, 40),
                fnr = "",
            )

        // Act
        val statusKontantstøtte = statusController.statusKontantstøtte()

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.OK)
        assertThat(logAppender.logEvents.filter { it.level == Level.WARN }.size).isEqualTo(1)
    }

    @Test
    fun `skal logge warning dersom det er dagtid og ikke helg og mer enn 20 minutter siden vi har mottatt en søknad for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        every { søknadRepository.finnSisteLagredeSøknad() } returns
            DBBarnetrygdSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 2, 9, 40),
                fnr = "",
            )

        // Act
        val statusBarnetrygd = statusController.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.OK)
        assertThat(logAppender.logEvents.filter { it.level == Level.WARN }.size).isEqualTo(1)
    }

    class LogAppender : AppenderBase<ILoggingEvent>() {
        val logEvents: MutableList<ILoggingEvent> = mutableListOf()

        override fun append(eventObject: ILoggingEvent) {
            logEvents.add(eventObject)
        }
    }
}
