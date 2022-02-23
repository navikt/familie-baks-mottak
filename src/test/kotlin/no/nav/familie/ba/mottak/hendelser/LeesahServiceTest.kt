package no.nav.familie.ba.mottak.hendelser

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.ba.mottak.task.MottaAnnullerFødselTask
import no.nav.familie.ba.mottak.task.MottaFødselshendelseTask
import no.nav.familie.ba.mottak.task.VurderLivshendelseTask
import no.nav.familie.prosessering.domene.Avvikstype
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.core.env.Environment
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextUInt

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LeesahServiceTest {

    lateinit var mockHendelsesloggRepository: HendelsesloggRepository
    lateinit var mockTaskRepository: TaskRepository
    lateinit var mockenv: Environment
    lateinit var service: LeesahService

    @BeforeEach
    internal fun setUp() {
        mockHendelsesloggRepository = mockk(relaxed = true)
        mockTaskRepository = mockk(relaxed = true)
        mockenv = mockk<Environment>(relaxed = true)
        service = LeesahService(mockHendelsesloggRepository, mockTaskRepository, 1, mockenv)
        clearAllMocks()
    }

    @Test
    fun `Skal opprette VurderLivshendelseTask for dødsfallhendelse`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_DØDSFALL,
                dødsdato = LocalDate.now())

        service.prosesserNyHendelse(pdlHendelse)

        val taskSlot = slot<Task>()
        verify {
            mockTaskRepository.save(capture(taskSlot))
        }
        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload).contains("\"personIdent\":\"12345678901\",\"type\":\"DØDSFALL\"")
        assertThat(taskSlot.captured.taskStepType).isEqualTo(VurderLivshendelseTask.TASK_STEP_TYPE)

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette VurderLivshendelseTask for utflyttingshendelse`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_UTFLYTTING,
                utflyttingsdato = LocalDate.now())

        service.prosesserNyHendelse(pdlHendelse)

        val taskSlot = slot<Task>()
        verify {
            mockTaskRepository.save(capture(taskSlot))
        }
        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload).contains("\"personIdent\":\"12345678901\",\"type\":\"UTFLYTTING\"")
        assertThat(taskSlot.captured.taskStepType).isEqualTo(VurderLivshendelseTask.TASK_STEP_TYPE)

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette VurderLivshendelseTask for sivilstandhendelse GIFT med triggerTid 1 time fra nå`() {
        val enTimeFraNå = LocalDateTime.now().plusHours(1)
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
            offset = Random.nextUInt().toLong(),
            gjeldendeAktørId = "1234567890123",
            hendelseId = hendelseId,
            personIdenter = listOf("12345678901", "1234567890123"),
            endringstype = LeesahService.OPPRETTET,
            opplysningstype = LeesahService.OPPLYSNINGSTYPE_SIVILSTAND,
            sivilstand = "GIFT",
            sivilstandDato = LocalDate.of(2022, 2, 22),
        )

        service.prosesserNyHendelse(pdlHendelse)
        service.prosesserNyHendelse(pdlHendelse.copy(sivilstand = "UOPPGITT"))

        val taskSlot = slot<Task>()
        verify(exactly = 1) {
            mockTaskRepository.save(capture(taskSlot))
        }
        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload)
            .isEqualTo("{\"personIdent\":\"12345678901\",\"type\":\"SIVILSTAND\",\"gyldigFom\":\"2022-02-22\"}")
        assertThat(taskSlot.captured.taskStepType).isEqualTo(VurderLivshendelseTask.TASK_STEP_TYPE)
        assertThat(taskSlot.captured.triggerTid).isBetween(enTimeFraNå, enTimeFraNå.plusMinutes(1))

        verify(exactly = 2) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette MottaFødselshendelseTask med fnr på payload`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
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

    @Test
    fun `Skal ignorere fødselshendelser utenfor norge`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_FØDSEL,
                fødselsdato = LocalDate.now(),
                fødeland = "POL")

        service.prosesserNyHendelse(pdlHendelse)

        verify(exactly = 0) { mockTaskRepository.save(any()) }

        service.prosesserNyHendelse(pdlHendelse.copy(fødeland = "NOR"))

        verify(exactly = 1) { mockTaskRepository.save(any()) }

        service.prosesserNyHendelse(pdlHendelse.copy(fødeland = null))

        verify(exactly = 2) { mockTaskRepository.save(any()) }
    }

    @Test
    fun `Skal opprette MottaAnnullerFødselTask når endringstype er ANNULLERT`(){
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
            offset = Random.nextUInt().toLong(),
            gjeldendeAktørId = "1234567890123",
            hendelseId = hendelseId,
            personIdenter = listOf("12345678901", "1234567890123"),
            endringstype = LeesahService.ANNULLERT,
            opplysningstype = LeesahService.OPPLYSNINGSTYPE_FØDSEL,
            fødselsdato = LocalDate.now(),
            fødeland = "NOR",
            tidligereHendelseId = "unknown")

        service.prosesserNyHendelse(pdlHendelse)

        val taskSlot = slot<Task>()
        verify {
            mockTaskRepository.save(capture(taskSlot))
        }

        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.metadata["tidligereHendelseId"]).isEqualTo("unknown")
        assertThat(taskSlot.captured.taskStepType).isEqualTo(MottaAnnullerFødselTask.TASK_STEP_TYPE)

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette ny task for korrigert sivilstandhendelse GIFT og avvikshåndtere uferdig task for korrigert eller annulert hendelse`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
            offset = Random.nextUInt().toLong(),
            gjeldendeAktørId = "1234567890123",
            hendelseId = hendelseId,
            personIdenter = listOf("12345678901", "1234567890123"),
            endringstype = LeesahService.OPPRETTET,
            opplysningstype = LeesahService.OPPLYSNINGSTYPE_SIVILSTAND,
            sivilstand = "GIFT",
            sivilstandDato = LocalDate.of(2022, 2, 22),
        )

        service.prosesserNyHendelse(pdlHendelse)


        val taskSlot = slot<Task>()
        verify(exactly = 1) {
            mockTaskRepository.save(capture(taskSlot))
        }
        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload)
            .isEqualTo("{\"personIdent\":\"12345678901\",\"type\":\"SIVILSTAND\",\"gyldigFom\":\"2022-02-22\"}")
        assertThat(taskSlot.captured.taskStepType).isEqualTo(VurderLivshendelseTask.TASK_STEP_TYPE)

        val tidligereTask = mockk<Task>(relaxed = true)
        every { tidligereTask.callId } returns hendelseId
        every { tidligereTask.taskStepType } returns VurderLivshendelseTask.TASK_STEP_TYPE

        every { mockTaskRepository.finnTasksMedStatus(any(), any()) } returns listOf(tidligereTask)

        val korrigertDato = pdlHendelse.sivilstandDato!!.minusDays(1)
        service.prosesserNyHendelse(pdlHendelse.copy(endringstype = LeesahService.KORRIGERT,
                                                     sivilstandDato = korrigertDato,
                                                     tidligereHendelseId = pdlHendelse.hendelseId))

        service.prosesserNyHendelse(pdlHendelse.copy(endringstype = LeesahService.ANNULLERT,
                                                     tidligereHendelseId = pdlHendelse.hendelseId))

        val tasks = mutableListOf<Task>()
        verify {
            mockTaskRepository.save(capture(tasks))
            tidligereTask.avvikshåndter(Avvikstype.ANNET, LeesahService.KORRIGERT, "VL")
            tidligereTask.avvikshåndter(Avvikstype.ANNET, LeesahService.ANNULLERT, "VL")
        }
        assertThat(tasks.find { it.payload.contains("$korrigertDato") }).isNotNull
    }
}