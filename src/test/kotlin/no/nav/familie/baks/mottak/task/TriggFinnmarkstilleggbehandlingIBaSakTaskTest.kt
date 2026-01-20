package no.nav.familie.baks.mottak.task

import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.prosessering.domene.Task
import org.junit.jupiter.api.Test

class TriggFinnmarkstilleggbehandlingIBaSakTaskTest {
    private val mockBaSakClient: BaSakClient = mockk()
    private val triggFinnmarkstilleggbehandlingIBaSakTask =
        TriggFinnmarkstilleggbehandlingIBaSakTask(
            baSakClient = mockBaSakClient,
        )

    private val personIdent = "12345678910"

    @Test
    fun `skal sende finnmarkstillegg til ba-sak når feature toggle er aktivert`() {
        // Arrange
        justRun { mockBaSakClient.sendFinnmarkstilleggTilBaSak(personIdent) }

        val task = Task(TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE, personIdent)

        // Act
        triggFinnmarkstilleggbehandlingIBaSakTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(personIdent) }
    }
}
