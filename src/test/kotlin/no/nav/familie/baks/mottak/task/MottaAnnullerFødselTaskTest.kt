package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.integrasjoner.RestAnnullerFødsel
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.log.mdc.MDCConstants
import no.nav.familie.prosessering.domene.Avvikstype
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import no.nav.familie.prosessering.rest.Ressurs
import no.nav.familie.prosessering.rest.RestTaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.MDC

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MottaAnnullerFødselTaskTest {
    @Test
    fun `Skal endre status av Task med riktig type`() {
        val taskService = mockk<TaskService>()
        val restTaskService = mockk<RestTaskService>()

        MDC.put(MDCConstants.MDC_CALL_ID, "ooo")
        val journalførSøknadTask =
            Task(type = JournalførSøknadTask.JOURNALFØR_SØKNAD, payload = "").copy(
                id = 0,
            )
        val sendTilBaSakTask =
            Task(type = SendTilBaSakTask.TASK_STEP_TYPE, payload = "").copy(
                id = 1,
            )
        val mottaFødselshendelseTask =
            Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = "").copy(
                id = 2,
            )
        every { taskService.finnTasksMedStatus(any(), any(), any()) } returns
            listOf(
                journalførSøknadTask,
                sendTilBaSakTask,
                mottaFødselshendelseTask,
            )
        every { taskService.findById(eq(0)) } returns journalførSøknadTask
        every { taskService.findById(eq(1)) } returns sendTilBaSakTask
        every { taskService.findById(eq(2)) } returns mottaFødselshendelseTask

        val taskSlot = mutableListOf<Task>()
        every { taskService.save(capture(taskSlot)) } returns mottaFødselshendelseTask
        every { restTaskService.avvikshåndterTask(any(), any(), any(), any()) } returns Ressurs.success("OK")

        MottaAnnullerFødselTask(taskService, restTaskService).doTask(
            Task(
                type = MottaAnnullerFødselTask.TASK_STEP_TYPE,
                payload = jsonMapper.writeValueAsString(RestAnnullerFødsel(listOf("12345678910"), "ooo")),
            ),
        )
        verify(exactly = 1) { restTaskService.avvikshåndterTask(1L, Avvikstype.ANNET, any(), any()) }
        verify(exactly = 1) { restTaskService.avvikshåndterTask(2L, Avvikstype.ANNET, any(), any()) }
    }

    @Test
    fun `Skal endre status av Task med riktig callId`() {
        val taskService = mockk<TaskService>()
        val restTaskService = mockk<RestTaskService>()

        MDC.put(MDCConstants.MDC_CALL_ID, "xxx")
        val task0 =
            Task(type = SendTilBaSakTask.TASK_STEP_TYPE, payload = "").copy(
                id = 0,
            )
        MDC.put(MDCConstants.MDC_CALL_ID, "ooo")
        val task1 =
            Task(type = SendTilBaSakTask.TASK_STEP_TYPE, payload = "").copy(
                id = 1,
            )

        every { taskService.finnTasksMedStatus(any(), any(), any()) } returns
            listOf(
                task0,
                task1,
            )
        every { taskService.findById(eq(0)) } returns task0
        every { taskService.findById(eq(1)) } returns task1

        val taskSlot = mutableListOf<Task>()
        every { taskService.save(capture(taskSlot)) } returns task0

        every { restTaskService.avvikshåndterTask(any(), any(), any(), any()) } returns Ressurs.success("OK")

        MottaAnnullerFødselTask(taskService, restTaskService).doTask(
            Task(
                type = MottaAnnullerFødselTask.TASK_STEP_TYPE,
                payload = jsonMapper.writeValueAsString(RestAnnullerFødsel(listOf("12345678910"), "ooo")),
            ),
        )
        verify(exactly = 1) { restTaskService.avvikshåndterTask(1L, any(), any(), any()) }
    }

    @Test
    fun `Skal finn Task med riktig Status`() {
        val taskService = mockk<TaskService>()
        val restTaskService = mockk<RestTaskService>()

        val statusSlot = slot<List<Status>>()
        every { taskService.finnTasksMedStatus(capture(statusSlot), any(), any()) } returns emptyList()
        MottaAnnullerFødselTask(taskService, restTaskService).doTask(
            Task(
                type = MottaAnnullerFødselTask.TASK_STEP_TYPE,
                payload = jsonMapper.writeValueAsString(RestAnnullerFødsel(listOf("12345678910"), "ooo")),
            ),
        )
        verify(exactly = 1) { taskService.finnTasksMedStatus(any(), any(), any()) }
        assertThat(statusSlot.captured.size).isEqualTo(3)
        assertThat(statusSlot.captured).contains(Status.KLAR_TIL_PLUKK)
        assertThat(statusSlot.captured).contains(Status.UBEHANDLET)
        assertThat(statusSlot.captured).contains(Status.FEILET)
    }
}
