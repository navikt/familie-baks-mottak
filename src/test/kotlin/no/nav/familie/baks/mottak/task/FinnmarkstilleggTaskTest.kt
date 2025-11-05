package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.ba.finnmarkstillegg.KommunerIFinnmarkOgNordTroms
import no.nav.familie.kontrakter.ba.finnmarkstillegg.KommunerIFinnmarkOgNordTroms.ALTA
import no.nav.familie.kontrakter.ba.finnmarkstillegg.KommunerIFinnmarkOgNordTroms.BERLEVÅG
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.byLessThan
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE
import org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE
import org.springframework.core.env.Environment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test

class FinnmarkstilleggTaskTest {
    private val mockBaSakClient: BaSakClient = mockk()
    private val mockPdlClient: PdlClient = mockk()
    private val mockTaskService: TaskService = mockk()
    private val mockEnvironment: Environment = mockk(relaxed = true)
    private val finnmarkstilleggTask = FinnmarkstilleggTask(mockPdlClient, mockBaSakClient, mockTaskService, mockEnvironment)
    private val personIdent = "123"
    private val osloKommunenummer = "0301"

    @BeforeEach
    fun setUp() {
        every { mockTaskService.finnTaskMedPayloadOgType(any(), any()) } returns null
        every { mockTaskService.save(any()) } returns mockk()
    }

    @Test
    fun `ikke opprett TriggFinnmarkstilleggbehandlingIBaSakTask hvis bostedskommune fra hendelse er null`() {
        // Arrange
        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, null, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 0) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @Test
    fun `ikke opprett TriggFinnmarkstilleggbehandlingIBaSakTask hvis bostedskommuneFomDato fra hendelse er null`() {
        // Arrange
        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, ALTA.kommunenummer, null)
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 0) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @ParameterizedTest
    @EnumSource(value = Status::class, names = ["KLAR_TIL_PLUKK", "UBEHANDLET"], mode = INCLUDE)
    fun `hopp ut av FinnmarkstilleggTask som følge av eksisterende ukjørt TriggFinnmarkstilleggbehandlingIBaSakTask for ident`(
        status: Status,
    ) {
        // Arrange
        every { mockTaskService.finnTaskMedPayloadOgType(any(), any()) } returns
            Task(
                type = TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE,
                payload = personIdent,
                status = status,
            )

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, ALTA.kommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockTaskService.finnTaskMedPayloadOgType(personIdent, TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE) }
        verify(exactly = 0) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @ParameterizedTest
    @EnumSource(value = Status::class, names = ["KLAR_TIL_PLUKK", "UBEHANDLET"], mode = EXCLUDE)
    fun `ikke hopp ut av FinnmarkstilleggTask som følge av eksisterende ferdigkjørt TriggFinnmarkstilleggbehandlingIBaSakTask for ident`(
        status: Status,
    ) {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns emptyList()
        every { mockTaskService.finnTaskMedPayloadOgType(any(), any()) } returns
            Task(
                type = TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE,
                payload = personIdent,
                status = status,
            )

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, ALTA.kommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockTaskService.finnTaskMedPayloadOgType(personIdent, TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE) }
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @Test
    fun `ìkke opprett TriggFinnmarkstilleggbehandlingIBaSakTask hvis person ikke har bostedsadresse`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns emptyList()

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, ALTA.kommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @Test
    fun `ikke opprett TriggFinnmarkstilleggbehandlingIBaSakTask hvis person flytter fra en kommune som skal ha Finnmarkstillegg til en annen kommune som har Finnmarkstillegg`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns ALTA.kommunenummer }))

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, BERLEVÅG.kommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @Test
    fun `ikke opprett TriggFinnmarkstilleggbehandlingIBaSakTask hvis person flytter fra en av kommune som ikke skal ha Finnmarkstillegg til en annen kommune som ikke har Finnmarkstillegg`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns osloKommunenummer }))

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, osloKommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @Test
    fun `ikke opprett TriggFinnmarkstilleggbehandlingIBaSakTask hvis person ikke har noen løpende saker`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns osloKommunenummer }))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns emptyList()

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, ALTA.kommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @ParameterizedTest
    @EnumSource(KommunerIFinnmarkOgNordTroms::class)
    fun `opprett TriggFinnmarkstilleggbehandlingIBaSakTask hvis person flytter fra en kommune som ikke har Finnmarkstillleg til en av kommune som skal ha Finnmarkstillegg`(input: KommunerIFinnmarkOgNordTroms) {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns osloKommunenummer }))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns listOf(mockk())

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, input.kommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        val taskSlot = slot<Task>()
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 1) { mockTaskService.save(capture(taskSlot)) }

        assertThat(taskSlot.captured.payload).isEqualTo(personIdent)
        assertThat(taskSlot.captured.type).isEqualTo(TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE)
    }

    @ParameterizedTest
    @EnumSource(KommunerIFinnmarkOgNordTroms::class)
    fun `opprett TriggFinnmarkstilleggbehandlingIBaSakTask hvis person flytter fra en av kommune som skal ha Finnmarkstillegg til en kommune som ikke har Finnmarkstillegg`(input: KommunerIFinnmarkOgNordTroms) {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns input.kommunenummer }))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns listOf(mockk())

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, osloKommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        val taskSlot = slot<Task>()
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 1) { mockTaskService.save(capture(taskSlot)) }

        assertThat(taskSlot.captured.payload).isEqualTo(personIdent)
        assertThat(taskSlot.captured.type).isEqualTo(TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE)
    }

    @Test
    fun `opprett TriggFinnmarkstilleggbehandlingIBaSakTask med riktig triggertid i prod`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns ALTA.kommunenummer }))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns listOf(mockk())
        every { mockEnvironment.activeProfiles } returns arrayOf("prod")

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, osloKommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        val taskSlot = slot<Task>()
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 1) { mockTaskService.save(capture(taskSlot)) }

        assertThat(taskSlot.captured.payload).isEqualTo(personIdent)
        assertThat(taskSlot.captured.type).isEqualTo(TriggFinnmarkstilleggbehandlingIBaSakTask.TASK_STEP_TYPE)
        assertThat(taskSlot.captured.triggerTid).isCloseTo(LocalDateTime.now().plusHours(1), byLessThan(1, ChronoUnit.MINUTES))
    }
}
