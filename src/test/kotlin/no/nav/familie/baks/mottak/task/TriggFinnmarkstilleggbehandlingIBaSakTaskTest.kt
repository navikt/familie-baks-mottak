package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig.Companion.SEND_BOSTEDSADRESSE_HENDELSER_TIL_BA_SAK
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.error.RekjørSenereException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class TriggFinnmarkstilleggbehandlingIBaSakTaskTest {
    private val mockBaSakClient: BaSakClient = mockk()
    private val mockUnleashService: UnleashNextMedContextService = mockk()
    private val triggFinnmarkstilleggbehandlingIBaSakTask =
        TriggFinnmarkstilleggbehandlingIBaSakTask(
            baSakClient = mockBaSakClient,
            unleashNextMedContextService = mockUnleashService,
        )

    private val personIdent = "12345678910"

    @Test
    fun `skal sende finnmarkstillegg til ba-sak når feature toggle er aktivert`() {
        // Arrange
        every { mockUnleashService.isEnabled(SEND_BOSTEDSADRESSE_HENDELSER_TIL_BA_SAK) } returns true
        justRun { mockBaSakClient.sendFinnmarkstilleggTilBaSak(personIdent) }

        val task = Task(TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE, personIdent)

        // Act
        triggFinnmarkstilleggbehandlingIBaSakTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockUnleashService.isEnabled(SEND_BOSTEDSADRESSE_HENDELSER_TIL_BA_SAK) }
        verify(exactly = 1) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(personIdent) }
    }

    @Test
    fun `skal kaste RekjørSenereException når feature toggle er deaktivert`() {
        // Arrange
        every { mockUnleashService.isEnabled(SEND_BOSTEDSADRESSE_HENDELSER_TIL_BA_SAK) } returns false

        val task = Task(TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE, personIdent)
        val omEnUke = LocalDate.now().plusWeeks(1)

        // Act & Assert
        val exception =
            assertThrows<RekjørSenereException> {
                triggFinnmarkstilleggbehandlingIBaSakTask.doTask(task)
            }

        assertThat(exception.årsak).isEqualTo("Toggle er skrudd av, prøver igjen om 1 uke")
        assertThat(exception.triggerTid.toLocalDate()).isEqualTo(omEnUke)

        verify(exactly = 1) { mockUnleashService.isEnabled(SEND_BOSTEDSADRESSE_HENDELSER_TIL_BA_SAK) }
        verify(exactly = 0) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }
}
