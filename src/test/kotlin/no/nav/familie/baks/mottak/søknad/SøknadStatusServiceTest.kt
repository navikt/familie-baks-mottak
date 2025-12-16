package no.nav.familie.baks.mottak.søknad

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
import java.time.LocalDateTime

class SøknadStatusServiceTest {
    private val søknadRepository = mockk<SøknadRepository>()
    private val kontantstøtteSøknadRepository = mockk<KontantstøtteSøknadRepository>()
    private val søknadStatusService = SøknadStatusService(søknadRepository, kontantstøtteSøknadRepository)

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
        every { kontantstøtteSøknadRepository.finnSisteLagredeSøknad() } returns
            DBKontantstøtteSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 1, 21, 59),
                fnr = "",
            )

        // Act
        val statusKontantstøtte = søknadStatusService.statusKontantstøtte()

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.DOWN)
    }

    @Test
    fun `skal gi status OK dersom det har kommet inn noen søknader på 12 timer for kontantstøtte`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        every { kontantstøtteSøknadRepository.finnSisteLagredeSøknad() } returns
            DBKontantstøtteSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 2, 8, 59),
                fnr = "",
            )

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
        every { søknadRepository.finnSisteLagredeSøknad() } returns
            DBBarnetrygdSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 1, 21, 59),
                fnr = "",
            )

        // Act
        val statusBarnetrygd = søknadStatusService.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.DOWN)
    }

    @Test
    fun `skal gi status OK dersom det har kommet inn noen søknader på 12 timer for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 10, 0)
        every { søknadRepository.finnSisteLagredeSøknad() } returns
            DBBarnetrygdSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 2, 8, 59),
                fnr = "",
            )

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
        every { kontantstøtteSøknadRepository.finnSisteLagredeSøknad() } returns
            DBKontantstøtteSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 1, 1, 59),
                fnr = "",
            )

        // Act
        val statusKontantstøtte = søknadStatusService.statusKontantstøtte()

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.DOWN)
    }

    @Test
    fun `skal gi status OK dersom det er natt og det har kommet inn noen søknader på 12 timer for kontantstøtte`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 2, 0)
        every { kontantstøtteSøknadRepository.finnSisteLagredeSøknad() } returns
            DBKontantstøtteSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 1, 14, 59),
                fnr = "",
            )

        // Act
        val statusKontantstøtte = søknadStatusService.statusKontantstøtte()
        println(statusKontantstøtte)

        // Assert
        assertThat(statusKontantstøtte.status).isEqualTo(Plattformstatus.OK)
        assertThat(statusKontantstøtte.description).isEqualTo("Alt er OK")
        assertThat(statusKontantstøtte.logLink).isNull()
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
        val statusBarnetrygd = søknadStatusService.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.DOWN)
    }

    @Test
    fun `skal gi status OK dersom det er natt og det ikke har kommet inn noen søknader på 24 timer for barnetrygd`() {
        // Arrange
        every { LocalDateTime.now() } returns LocalDateTime.of(2024, 8, 2, 2, 0)
        every { søknadRepository.finnSisteLagredeSøknad() } returns
            DBBarnetrygdSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 1, 14, 59),
                fnr = "",
            )

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
        every { kontantstøtteSøknadRepository.finnSisteLagredeSøknad() } returns
            DBKontantstøtteSøknad(
                søknadJson = "",
                opprettetTid = LocalDateTime.of(2024, 8, 1, 13, 59),
                fnr = "",
            )

        // Act
        val statusKontantstøtte = søknadStatusService.statusKontantstøtte()

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
        val statusBarnetrygd = søknadStatusService.statusBarnetrygd()

        // Assert
        assertThat(statusBarnetrygd.status).isEqualTo(Plattformstatus.ISSUE)
    }
}
