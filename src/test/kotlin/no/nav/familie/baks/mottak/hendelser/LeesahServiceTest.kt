package no.nav.familie.baks.mottak.hendelser

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.domene.HendelsesloggRepository
import no.nav.familie.baks.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.baks.mottak.task.FinnmarkstilleggTask
import no.nav.familie.baks.mottak.task.MottaAnnullerFødselTask
import no.nav.familie.baks.mottak.task.MottaFødselshendelseTask
import no.nav.familie.baks.mottak.task.VurderBarnetrygdLivshendelseTask
import no.nav.familie.baks.mottak.task.VurderFinnmarkstillleggTaskDTO
import no.nav.familie.baks.mottak.task.VurderKontantstøtteLivshendelseTask
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE
import org.springframework.core.env.Environment
import java.time.LocalDate
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

    @Nested
    inner class FinnmarkstilleggTaskTest {
        private val ident = "12345678910"
        private val bostedskommune = "0301"
        private val bostedskommuneFomDato = LocalDate.of(2025, 1, 1)

        private val hendelseId = UUID.randomUUID().toString()
        private val tidligereHendelseId = UUID.randomUUID().toString()

        @Test
        fun `Skal opprette ny FinnmarkstilleggTask når tidligereHendelseId er null`() {
            // Arrange
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
                    tidligereHendelseId = null,
                )

            // Act
            service.prosesserNyHendelse(pdlHendelse)

            // Assert
            val taskSlot = slot<Task>()
            verify { mockTaskService.save(capture(taskSlot)) }

            assertFinnmarkstilleggTask(task = taskSlot.captured)
        }

        @Test
        fun `Skal opprette ny FinnmarkstilleggTask når tidligereHendelseId finnes, men ingen task med matchende callId`() {
            // Arrange
            every { mockTaskService.finnAlleTasksMedCallId(tidligereHendelseId) } returns emptyList()

            val pdlHendelse =
                PdlHendelse(
                    offset = Random.nextLong(),
                    gjeldendeAktørId = ident,
                    hendelseId = hendelseId,
                    personIdenter = listOf(ident),
                    endringstype = LeesahService.KORRIGERT,
                    opplysningstype = LeesahService.OPPLYSNINGSTYPE_BOSTEDSADRESSE,
                    bostedskommune = bostedskommune,
                    bostedskommuneFomDato = bostedskommuneFomDato,
                    tidligereHendelseId = tidligereHendelseId,
                )

            // Act
            service.prosesserNyHendelse(pdlHendelse)

            // Assert
            val taskSlot = slot<Task>()
            verify { mockTaskService.save(capture(taskSlot)) }

            assertFinnmarkstilleggTask(task = taskSlot.captured)
        }

        @ParameterizedTest
        @EnumSource(value = Status::class, names = ["KLAR_TIL_PLUKK", "UBEHANDLET"], mode = EXCLUDE)
        fun `Skal opprette ny FinnmarkstilleggTask når eksisterende task har feil status`(
            status: Status,
        ) {
            // Arrange
            val tidligereHendelse =
                Task(
                    id = 1L,
                    type = FinnmarkstilleggTask.TASK_STEP_TYPE,
                    payload = "{\"ident\":\"$ident\",\"bostedskommune\":\"$bostedskommune\",\"bostedskommuneFomDato\":\"$bostedskommuneFomDato\"}",
                    status = status,
                ).apply {
                    metadata.setProperty("ident", ident)
                    metadata.setProperty("callId", tidligereHendelseId)
                }

            every { mockTaskService.finnAlleTasksMedCallId(tidligereHendelseId) } returns listOf(tidligereHendelse)

            val nyBostedskommune = "0302"
            val pdlHendelse =
                PdlHendelse(
                    offset = Random.nextLong(),
                    gjeldendeAktørId = ident,
                    hendelseId = hendelseId,
                    personIdenter = listOf(ident),
                    endringstype = LeesahService.OPPRETTET,
                    opplysningstype = LeesahService.OPPLYSNINGSTYPE_BOSTEDSADRESSE,
                    bostedskommune = nyBostedskommune,
                    bostedskommuneFomDato = bostedskommuneFomDato,
                    tidligereHendelseId = null,
                )

            // Act
            service.prosesserNyHendelse(pdlHendelse)

            // Assert
            val taskSlot = slot<Task>()
            verify { mockTaskService.save(capture(taskSlot)) }

            assertFinnmarkstilleggTask(task = taskSlot.captured, forventetBostedskommune = nyBostedskommune)
        }

        @Test
        fun `Skal filtrere bort tidligere tasks som ikke er FinnmarkstilleggTask type`() {
            // Arrange
            val annenTypeTask =
                Task(
                    id = 1L,
                    type = "annenTaskType",
                    payload = "payload",
                )

            every { mockTaskService.finnAlleTasksMedCallId(tidligereHendelseId) } returns listOf(annenTypeTask)

            val pdlHendelse =
                PdlHendelse(
                    offset = Random.nextLong(),
                    gjeldendeAktørId = ident,
                    hendelseId = hendelseId,
                    personIdenter = listOf(ident),
                    endringstype = LeesahService.KORRIGERT,
                    opplysningstype = LeesahService.OPPLYSNINGSTYPE_BOSTEDSADRESSE,
                    bostedskommune = bostedskommune,
                    bostedskommuneFomDato = bostedskommuneFomDato,
                    tidligereHendelseId = tidligereHendelseId,
                )

            // Act
            service.prosesserNyHendelse(pdlHendelse)

            // Assert
            val taskSlot = slot<Task>()
            verify { mockTaskService.save(capture(taskSlot)) }

            assertFinnmarkstilleggTask(taskSlot.captured)
        }

        @Test
        fun `Skal oppdatere eksisterende FinnmarkstilleggTask når eksisterende task har ulik payload`() {
            // Arrange
            val eksisterendeTask =
                Task(
                    id = 1L,
                    type = FinnmarkstilleggTask.TASK_STEP_TYPE,
                    payload = "{\"ident\":\"$ident\",\"bostedskommune\":\"$bostedskommune\",\"bostedskommuneFomDato\":\"$bostedskommuneFomDato\"}",
                ).apply {
                    metadata.setProperty("ident", ident)
                    metadata.setProperty("callId", tidligereHendelseId)
                }

            every { mockTaskService.finnAlleTasksMedCallId(tidligereHendelseId) } returns listOf(eksisterendeTask)

            val nyBostedskommune = "0302"

            val pdlHendelse =
                PdlHendelse(
                    offset = Random.nextLong(),
                    gjeldendeAktørId = ident,
                    hendelseId = hendelseId,
                    personIdenter = listOf(ident),
                    endringstype = LeesahService.KORRIGERT,
                    opplysningstype = LeesahService.OPPLYSNINGSTYPE_BOSTEDSADRESSE,
                    bostedskommune = nyBostedskommune,
                    bostedskommuneFomDato = bostedskommuneFomDato,
                    tidligereHendelseId = tidligereHendelseId,
                )

            // Act
            service.prosesserNyHendelse(pdlHendelse)

            // Assert
            val taskSlot = slot<Task>()
            verify { mockTaskService.save(capture(taskSlot)) }

            assertFinnmarkstilleggTask(task = taskSlot.captured, forventetTaskId = 1L, forventetBostedskommune = nyBostedskommune)
        }

        @Test
        fun `Skal ikke opprette eller oppdatere FinnmarkstilleggTask når eksisterende task har samme payload`() {
            // Arrange
            val eksisterendeTask =
                Task(
                    type = "finnmarkstilleggTask",
                    payload = "{\"ident\":\"$ident\",\"bostedskommune\":\"$bostedskommune\",\"bostedskommuneFomDato\":\"$bostedskommuneFomDato\"}",
                ).apply {
                    metadata.setProperty("ident", ident)
                    metadata.setProperty("callId", tidligereHendelseId)
                }

            every { mockTaskService.finnAlleTasksMedCallId(tidligereHendelseId) } returns listOf(eksisterendeTask)

            val pdlHendelse =
                PdlHendelse(
                    offset = Random.nextLong(),
                    gjeldendeAktørId = ident,
                    hendelseId = hendelseId,
                    personIdenter = listOf(ident),
                    endringstype = LeesahService.KORRIGERT,
                    opplysningstype = LeesahService.OPPLYSNINGSTYPE_BOSTEDSADRESSE,
                    bostedskommune = bostedskommune,
                    bostedskommuneFomDato = bostedskommuneFomDato,
                    tidligereHendelseId = tidligereHendelseId,
                )

            // Act
            service.prosesserNyHendelse(pdlHendelse)

            // Assert
            verify(exactly = 0) { mockTaskService.save(any()) }
        }

        private fun assertFinnmarkstilleggTask(
            task: Task,
            forventetTaskId: Long = 0L,
            forventetIdent: String = ident,
            forventetBostedskommune: String? = bostedskommune,
            forventetBostedskommuneFomDato: LocalDate? = bostedskommuneFomDato,
            forventetCallId: String? = hendelseId,
        ) {
            val payload = objectMapper.readValue(task.payload, VurderFinnmarkstillleggTaskDTO::class.java)
            assertThat(task.id).isEqualTo(forventetTaskId)
            assertThat(payload.ident).isEqualTo(forventetIdent)
            assertThat(payload.bostedskommune).isEqualTo(forventetBostedskommune)
            assertThat(payload.bostedskommuneFomDato).isEqualTo(forventetBostedskommuneFomDato)
            assertThat(task.metadata["callId"]).isEqualTo(forventetCallId)
            assertThat(task.metadata["ident"]).isEqualTo(forventetIdent)
        }
    }
}
