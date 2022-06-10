package no.nav.familie.ba.mottak.task

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.ba.mottak.integrasjoner.RestAnnullerFødsel
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.log.mdc.MDCConstants
import no.nav.familie.prosessering.domene.Avvikstype
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.MDC
import java.util.Optional

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MottaAnnullerFødselTaskTest {

    private val sakClient = mockk<SakClient>()

    @Test
    fun `Skal endre status av Task med riktig type`() {
        val taskRepository = mockk<TaskRepository>()

        val journalførSøknadTask = Task(type = JournalførSøknadTask.JOURNALFØR_SØKNAD, payload = "").copy(
            id = 0
        )
        val sendTilSakTask = Task(type = SendTilSakTask.TASK_STEP_TYPE, payload = "").copy(
            id = 1
        )
        val mottaFødselshendelseTask = Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = "").copy(
            id = 2
        )
        every { taskRepository.findByStatusIn(any(), any()) } returns listOf(
            journalførSøknadTask,
            sendTilSakTask,
            mottaFødselshendelseTask,
        )
        every { taskRepository.findById(eq(0)) } returns Optional.of(journalførSøknadTask)
        every { taskRepository.findById(eq(1)) } returns Optional.of(sendTilSakTask)
        every { taskRepository.findById(eq(2)) } returns Optional.of(mottaFødselshendelseTask)

        val taskSlot = mutableListOf<Task>()
        every { taskRepository.save(capture(taskSlot)) } returns mottaFødselshendelseTask

        MottaAnnullerFødselTask(taskRepository, sakClient).doTask(
            Task(
                type = MottaAnnullerFødselTask.TASK_STEP_TYPE,
                payload = objectMapper.writeValueAsString(RestAnnullerFødsel(listOf("12345678910"), "ooo"))
            )
        )
        verify(exactly = 2) { taskRepository.save(any()) }
        assertThat(taskSlot.find { it.id == 1L }!!.status).isEqualTo(Status.AVVIKSHÅNDTERT)
        assertThat(taskSlot.find { it.id == 1L }!!.avvikstype).isEqualTo(Avvikstype.ANNET)
        var logg = taskSlot.find { it.id == 1L }!!.logg.toList()
        var avvikslogg = logg.get(logg.size - 1)
        assertThat(avvikslogg.endretAv).isEqualTo("VL")
        assertThat(avvikslogg.melding).isEqualTo(MottaAnnullerFødselTask.AVVIKSÅRSAK)

        assertThat(taskSlot.find { it.id == 2L }!!.status).isEqualTo(Status.AVVIKSHÅNDTERT)
        assertThat(taskSlot.find { it.id == 2L }!!.avvikstype).isEqualTo(Avvikstype.ANNET)
    }

    @Test
    fun `Skal endre status av Task med riktig callId`() {
        val taskRepository = mockk<TaskRepository>()

        MDC.put(MDCConstants.MDC_CALL_ID, "xxx")
        val task0 = Task(type = SendTilSakTask.TASK_STEP_TYPE, payload = "").copy(
            id = 0
        )
        MDC.put(MDCConstants.MDC_CALL_ID, "ooo")
        val task1 = Task(type = SendTilSakTask.TASK_STEP_TYPE, payload = "").copy(
            id = 1
        )

        every { taskRepository.findByStatusIn(any(), any()) } returns listOf(
            task0,
            task1,
        )
        every { taskRepository.findById(eq(0)) } returns Optional.of(task0)
        every { taskRepository.findById(eq(1)) } returns Optional.of(task1)

        val taskSlot = mutableListOf<Task>()
        every { taskRepository.save(capture(taskSlot)) } returns task0

        MottaAnnullerFødselTask(taskRepository, sakClient).doTask(
            Task(
                type = MottaAnnullerFødselTask.TASK_STEP_TYPE,
                payload = objectMapper.writeValueAsString(RestAnnullerFødsel(listOf("12345678910"), "ooo")),
            )
        )
        verify(exactly = 1) { taskRepository.save(any()) }
        assertThat(taskSlot.find { it.id == 1L }!!.status).isEqualTo(Status.AVVIKSHÅNDTERT)
        assertThat(taskSlot.find { it.id == 1L }!!.avvikstype).isEqualTo(Avvikstype.ANNET)
    }

    @Test
    fun `Skal finn Task med riktig Status`() {
        val taskRepository = mockk<TaskRepository>()

        val statusSlot = slot<List<Status>>()
        every { taskRepository.findByStatusIn(capture(statusSlot), any()) } returns emptyList()
        MottaAnnullerFødselTask(taskRepository, sakClient).doTask(
            Task(
                type = MottaAnnullerFødselTask.TASK_STEP_TYPE,
                payload = objectMapper.writeValueAsString(RestAnnullerFødsel(listOf("12345678910"), "ooo")),
            )
        )
        verify(exactly = 1) { taskRepository.findByStatusIn(any(), any()) }
        assertThat(statusSlot.captured.size).isEqualTo(3)
        assertThat(statusSlot.captured).contains(Status.KLAR_TIL_PLUKK)
        assertThat(statusSlot.captured).contains(Status.UBEHANDLET)
        assertThat(statusSlot.captured).contains(Status.FEILET)
    }

    @Test
    fun `Skal send annuller fødsel til ba-sak hvis det er ikke finnes åpen Task i repository`() {
        val taskRepository = mockk<TaskRepository>()

        every { taskRepository.findByStatusIn(any(), any()) } returns emptyList()
        val barnasIdenterSlot = slot<List<String>>()
        val tidligereHendelseIdSlot = slot<String>()
        every {
            sakClient.sendAnnullerFødselshendelseTilSak(
                capture(barnasIdenterSlot),
                capture(tidligereHendelseIdSlot)
            )
        } just runs

        val barnsIdenter = listOf("12345678910", "12345678911")
        MottaAnnullerFødselTask(taskRepository, sakClient).doTask(
            Task(
                type = MottaAnnullerFødselTask.TASK_STEP_TYPE,
                payload = objectMapper.writeValueAsString(RestAnnullerFødsel(barnsIdenter, "ooo")),
            )
        )

        assertThat(barnasIdenterSlot.captured).hasSize(2)
        assertThat(barnasIdenterSlot.captured.any { it == barnsIdenter[0] }).isTrue()
        assertThat(barnasIdenterSlot.captured.any { it == barnsIdenter[1] }).isTrue()
        assertThat(tidligereHendelseIdSlot.captured).isEqualTo("ooo")
    }
}
