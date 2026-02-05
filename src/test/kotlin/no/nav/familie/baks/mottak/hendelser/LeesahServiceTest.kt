package no.nav.familie.baks.mottak.hendelser

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.domene.HendelsesloggRepository
import no.nav.familie.baks.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.baks.mottak.task.MottaAnnullerFødselTask
import no.nav.familie.baks.mottak.task.MottaFødselshendelseTask
import no.nav.familie.baks.mottak.task.VurderBarnetrygdLivshendelseTask
import no.nav.familie.baks.mottak.task.VurderFinnmarkstillleggTaskDTO
import no.nav.familie.baks.mottak.task.VurderKontantstøtteLivshendelseTask
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.byLessThan
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.core.env.Environment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.MINUTES
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextUInt

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LeesahServiceTest {
    lateinit var mockHendelsesloggRepository: HendelsesloggRepository
    lateinit var mockTaskService: TaskService
    lateinit var mockenv: Environment
    lateinit var service: LeesahService

    @BeforeEach
    internal fun setUp() {
        mockHendelsesloggRepository = mockk(relaxed = true)
        mockTaskService = mockk(relaxed = true)
        mockenv = mockk<Environment>(relaxed = true)
        service = LeesahService(mockHendelsesloggRepository, mockTaskService, 1, mockenv)
        clearAllMocks()
        every {
            mockTaskService.save(any<Task>())
        } returns Task("dummy", "payload")
    }

    @Test
    fun `Skal opprette VurderBarnetrygdLivshendelseTask og VurderKontantstøtteLivshendelseTask for dødsfallhendelse`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_DØDSFALL,
                dødsdato = LocalDate.now(),
            )

        service.prosesserNyHendelse(pdlHendelse)

        val taskList = mutableListOf<Task>()
        verify {
            mockTaskService.save(capture(taskList))
        }
        assertThat(taskList[0]).isNotNull
        assertThat(taskList[0].payload).contains("\"personIdent\":\"12345678901\",\"type\":\"DØDSFALL\"")
        assertThat(taskList[0].type).isEqualTo(VurderBarnetrygdLivshendelseTask.TASK_STEP_TYPE)

        assertThat(taskList[1]).isNotNull
        assertThat(taskList[1].payload).contains("\"personIdent\":\"12345678901\",\"type\":\"DØDSFALL\"")
        assertThat(taskList[1].type).isEqualTo(VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE)

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette VurderBarnetrygdLivshendelseTask og VurderKontantstøtteLivshendelseTask for utflyttingshendelse`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_UTFLYTTING,
                utflyttingsdato = LocalDate.now(),
            )

        service.prosesserNyHendelse(pdlHendelse)

        val taskList = mutableListOf<Task>()

        verify {
            mockTaskService.save(capture(taskList))
        }
        assertThat(taskList[0]).isNotNull
        assertThat(taskList[0].payload).contains("\"personIdent\":\"12345678901\",\"type\":\"UTFLYTTING\"")
        assertThat(taskList[0].type).isEqualTo(VurderBarnetrygdLivshendelseTask.TASK_STEP_TYPE)

        assertThat(taskList[1]).isNotNull
        assertThat(taskList[1].payload).contains("\"personIdent\":\"12345678901\",\"type\":\"UTFLYTTING\"")
        assertThat(taskList[1].type).isEqualTo(VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE)

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette VurderBarnetrygdLivshendelseTask for sivilstandhendelse GIFT`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse =
            PdlHendelse(
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
            mockTaskService.save(capture(taskSlot))
        }
        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload)
            .isEqualTo("{\"personIdent\":\"12345678901\",\"type\":\"SIVILSTAND\"}")
        assertThat(taskSlot.captured.type).isEqualTo(VurderBarnetrygdLivshendelseTask.TASK_STEP_TYPE)

        verify(exactly = 2) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette MottaFødselshendelseTask med fnr på payload`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_FØDSELSDATO,
                fødselsdato = LocalDate.now(),
                fødeland = "NOR",
            )

        service.prosesserNyHendelse(pdlHendelse)

        val taskSlot = slot<Task>()
        verify {
            mockTaskService.save(capture(taskSlot))
        }

        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload).isEqualTo("12345678901")
        assertThat(taskSlot.captured.type).isEqualTo(MottaFødselshendelseTask.TASK_STEP_TYPE)

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette MottaAnnullerFødselTask når endringstype er ANNULLERT`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.ANNULLERT,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_FØDSELSDATO,
                fødselsdato = LocalDate.now(),
                fødeland = "NOR",
                tidligereHendelseId = "unknown",
            )

        service.prosesserNyHendelse(pdlHendelse)

        val taskSlot = slot<Task>()
        verify {
            mockTaskService.save(capture(taskSlot))
        }

        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.metadata["tidligereHendelseId"]).isEqualTo("unknown")
        assertThat(taskSlot.captured.type).isEqualTo(MottaAnnullerFødselTask.TASK_STEP_TYPE)

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette FinnmarkstilleggTask for OPPLYSNINGSTYPE_BOSTEDSADRESSE`() {
        // Arrange
        val ident = "12345678910"
        val bostedskommune = "0301"
        val bostedskommuneFomDato = LocalDate.of(2025, 1, 1)
        val hendelseId = UUID.randomUUID().toString()

        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextLong(),
                gjeldendeAktørId = ident,
                hendelseId = hendelseId,
                personIdenter = listOf(ident),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_BOSTEDSADRESSE,
                bostedskommune = bostedskommune,
                bostedskommuneFomDato = bostedskommuneFomDato,
            )

        every { mockenv.activeProfiles } returns arrayOf("prod")

        // Act
        service.prosesserNyHendelse(pdlHendelse)

        // Assert
        val taskSlot = slot<Task>()
        verify(exactly = 1) { mockTaskService.save(capture(taskSlot)) }

        val task = taskSlot.captured
        assertThat(task.id).isEqualTo(0L)
        assertThat(task.metadata["callId"]).isEqualTo(hendelseId)
        assertThat(task.metadata["ident"]).isEqualTo(ident)
        assertThat(task.triggerTid).isCloseTo(LocalDateTime.now(), byLessThan(3, MINUTES))

        val payload = jsonMapper.readValue(taskSlot.captured.payload, VurderFinnmarkstillleggTaskDTO::class.java)
        assertThat(payload.ident).isEqualTo(ident)
        assertThat(payload.bostedskommune).isEqualTo(bostedskommune)
        assertThat(payload.bostedskommuneFomDato).isEqualTo(bostedskommuneFomDato)
    }

    @Test
    fun `Skal opprette SvalbardtilleggTask for OPPLYSNINGSTYPE_OPPHOLDSADRESSE`() {
        // Arrange
        val ident = "12345678910"
        val hendelseId = UUID.randomUUID().toString()

        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextLong(),
                gjeldendeAktørId = ident,
                hendelseId = hendelseId,
                personIdenter = listOf(ident),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_OPPHOLDSADRESSE,
            )

        every { mockenv.activeProfiles } returns arrayOf("prod")

        // Act
        service.prosesserNyHendelse(pdlHendelse)

        // Assert
        val taskSlot = slot<Task>()
        verify(exactly = 1) { mockTaskService.save(capture(taskSlot)) }

        val task = taskSlot.captured
        assertThat(task.id).isEqualTo(0L)
        assertThat(task.metadata["callId"]).isEqualTo(hendelseId)
        assertThat(task.metadata["ident"]).isEqualTo(ident)
        assertThat(task.triggerTid).isCloseTo(LocalDateTime.now(), byLessThan(3, MINUTES))
        assertThat(task.payload).isEqualTo(ident)
    }
}
