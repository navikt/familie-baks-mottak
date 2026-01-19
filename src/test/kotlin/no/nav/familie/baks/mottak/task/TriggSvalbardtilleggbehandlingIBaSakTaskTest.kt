package no.nav.familie.baks.mottak.task

import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.prosessering.domene.Task
import org.junit.jupiter.api.Test

class TriggSvalbardtilleggbehandlingIBaSakTaskTest {
    private val mockBaSakClient: BaSakClient = mockk()
    private val triggSvalbardtilleggbehandlingIBaSakTask =
        TriggSvalbardtilleggbehandlingIBaSakTask(
            baSakClient = mockBaSakClient,
        )

    private val personIdent = "12345678910"

    @Test
    fun `skal sende svalbardtillegg til ba-sak når feature toggle er aktivert`() {
        // Arrange
        justRun { mockBaSakClient.sendSvalbardtilleggTilBaSak(personIdent) }

        val task = Task(TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE, personIdent)

        // Act
        triggSvalbardtilleggbehandlingIBaSakTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockBaSakClient.sendSvalbardtilleggTilBaSak(personIdent) }
    }
}
