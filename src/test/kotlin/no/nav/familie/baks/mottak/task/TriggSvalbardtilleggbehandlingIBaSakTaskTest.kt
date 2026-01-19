package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggle.SEND_OPPHOLDSADRESSE_HENDELSER_TIL_BA_SAK
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleService
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.error.RekjørSenereException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class TriggSvalbardtilleggbehandlingIBaSakTaskTest {
    private val mockBaSakClient: BaSakClient = mockk()
    private val featureToggleService: FeatureToggleService = mockk()
    private val triggSvalbardtilleggbehandlingIBaSakTask =
        TriggSvalbardtilleggbehandlingIBaSakTask(
            baSakClient = mockBaSakClient,
            featureToggleService = featureToggleService,
        )

    private val personIdent = "12345678910"

    @Test
    fun `skal sende svalbardtillegg til ba-sak når feature toggle er aktivert`() {
        // Arrange
        every { featureToggleService.isEnabled(SEND_OPPHOLDSADRESSE_HENDELSER_TIL_BA_SAK) } returns true
        justRun { mockBaSakClient.sendSvalbardtilleggTilBaSak(personIdent) }

        val task = Task(TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE, personIdent)

        // Act
        triggSvalbardtilleggbehandlingIBaSakTask.doTask(task)

        // Assert
        verify(exactly = 1) { featureToggleService.isEnabled(SEND_OPPHOLDSADRESSE_HENDELSER_TIL_BA_SAK) }
        verify(exactly = 1) { mockBaSakClient.sendSvalbardtilleggTilBaSak(personIdent) }
    }

    @Test
    fun `skal kaste RekjørSenereException når feature toggle er deaktivert`() {
        // Arrange
        every { featureToggleService.isEnabled(SEND_OPPHOLDSADRESSE_HENDELSER_TIL_BA_SAK) } returns false

        val task = Task(TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE, personIdent)
        val omEnUke = LocalDate.now().plusWeeks(1)

        // Act & Assert
        val exception =
            assertThrows<RekjørSenereException> {
                triggSvalbardtilleggbehandlingIBaSakTask.doTask(task)
            }

        assertThat(exception.årsak).isEqualTo("Toggle er skrudd av, prøver igjen om 1 uke")
        assertThat(exception.triggerTid.toLocalDate()).isEqualTo(omEnUke)

        verify(exactly = 1) { featureToggleService.isEnabled(SEND_OPPHOLDSADRESSE_HENDELSER_TIL_BA_SAK) }
        verify(exactly = 0) { mockBaSakClient.sendSvalbardtilleggTilBaSak(any()) }
    }
}
