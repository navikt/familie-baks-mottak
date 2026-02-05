package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.prosessering.domene.Task
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VurderKontantstøtteLivshendelseTaskTest {
    private val vurderLivshendelseService: VurderLivshendelseService = mockk(relaxed = true)

    private val vurderKontantstøtteLivshendelseTask =
        VurderKontantstøtteLivshendelseTask(vurderLivshendelseService)

    @Test
    fun `Skal kalle videre på livshendelse service med tema BAR`() {
        val livshendelseTask =
            Task(
                type = VurderBarnetrygdLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    jsonMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            "123",
                            VurderLivshendelseType.DØDSFALL,
                        ),
                    ),
            )

        every { vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask, Tema.KON) } just runs

        vurderKontantstøtteLivshendelseTask.doTask(livshendelseTask)

        verify(exactly = 1) { vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask, Tema.KON) }
    }
}
