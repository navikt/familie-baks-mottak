package no.nav.familie.ba.mottak.hendelser

import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.ba.mottak.task.MottaFødselshendelseTask
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.util.*
import kotlin.random.Random
import kotlin.random.nextUInt

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LeesahServiceTest {

    lateinit var mockHendelsesloggRepository: HendelsesloggRepository
    lateinit var mockTaskRepository: TaskRepository
    lateinit var service: LeesahService

    @BeforeEach
    internal fun setUp() {
        mockHendelsesloggRepository = mockk(relaxed = true)
        mockTaskRepository = mockk(relaxed = true)
        service = LeesahService(mockHendelsesloggRepository, mockTaskRepository, 1)
        clearAllMocks()
    }

    @Test
    fun `Skal logge dødsfallhendelser`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
                offset = Random.nextUInt().toLong(),
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_DØDSFALL,
                dødsdato = LocalDate.now())

        service.prosesserNyHendelse(pdlHendelse)

        verify(exactly = 0) {
            mockTaskRepository.save(any())
        }

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette MottaFødselshendelseTask med fnr på payload`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
                offset = Random.nextUInt().toLong(),
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_FØDSEL,
                fødselsdato = LocalDate.now(),
                fødeland = "NOR")

        service.prosesserNyHendelse(pdlHendelse)

        val taskSlot = slot<Task>()
        verify {
            mockTaskRepository.save(capture(taskSlot))
        }

        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload).isEqualTo("12345678901")
        assertThat(taskSlot.captured.taskStepType).isEqualTo(MottaFødselshendelseTask.TASK_STEP_TYPE)

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }
}