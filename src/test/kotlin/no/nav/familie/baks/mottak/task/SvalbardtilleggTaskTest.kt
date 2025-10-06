package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.OppholdAnnetSted
import no.nav.familie.kontrakter.felles.personopplysning.Oppholdsadresse
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE
import org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE
import org.springframework.core.env.Environment
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test

class SvalbardtilleggTaskTest {
    private val mockBaSakClient: BaSakClient = mockk()
    private val mockPdlClient: PdlClient = mockk()
    private val mockTaskService: TaskService = mockk()
    private val mockEnvironment: Environment = mockk(relaxed = true)
    private val svalbardtilleggTask = SvalbardtilleggTask(mockPdlClient, mockBaSakClient, mockTaskService, mockEnvironment)
    private val personIdent = "123"

    @BeforeEach
    fun setUp() {
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse } returns
            listOf(Oppholdsadresse(gyldigFraOgMed = LocalDate.of(2025, 1, 1), oppholdAnnetSted = OppholdAnnetSted.PAA_SVALBARD.name))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns listOf(mockk())
        every { mockTaskService.finnTaskMedPayloadOgType(any(), any()) } returns null
        every { mockTaskService.save(any()) } returns mockk()
    }

    @Test
    fun `ìkke opprett TriggSvalbardtilleggbehandlingIBaSakTask hvis person ikke har oppholdsadresse`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse } returns emptyList()

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 0) { mockTaskService.finnTaskMedPayloadOgType(personIdent, TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @Test
    fun `ìkke opprett TriggSvalbardtilleggbehandlingIBaSakTask hvis person har oppholdsadresse annet sted enn Svalbard`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse } returns
            listOf(Oppholdsadresse(gyldigFraOgMed = LocalDate.of(2025, 1, 1), oppholdAnnetSted = OppholdAnnetSted.UTENRIKS.name))

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 0) { mockTaskService.finnTaskMedPayloadOgType(personIdent, TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @ParameterizedTest
    @EnumSource(value = Status::class, names = ["KLAR_TIL_PLUKK", "UBEHANDLET"], mode = INCLUDE)
    fun `hopp ut av SvalbardtilleggTask som følge av eksisterende ukjørt TriggSvalbardtilleggbehandlingIBaSakTask for ident`(
        status: Status,
    ) {
        // Arrange
        every { mockTaskService.finnTaskMedPayloadOgType(any(), any()) } returns
            Task(
                type = TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE,
                payload = personIdent,
                status = status,
            )

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 1) { mockTaskService.finnTaskMedPayloadOgType(personIdent, TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @ParameterizedTest
    @EnumSource(value = Status::class, names = ["KLAR_TIL_PLUKK", "UBEHANDLET"], mode = EXCLUDE)
    fun `ikke hopp ut av SvalbardtilleggTask som følge av eksisterende ferdigkjørt TriggSvalbardtilleggbehandlingIBaSakTask for ident`(
        status: Status,
    ) {
        // Arrange
        every { mockTaskService.finnTaskMedPayloadOgType(any(), any()) } returns
            Task(
                type = TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE,
                payload = personIdent,
                status = status,
            )
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns emptyList()

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 1) { mockTaskService.finnTaskMedPayloadOgType(personIdent, TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @Test
    fun `ikke opprett TriggSvalbardtilleggbehandlingIBaSakTask hvis person ikke har noen løpende saker`() {
        // Arrange
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns emptyList()

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 1) { mockTaskService.finnTaskMedPayloadOgType(personIdent, TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 0) { mockTaskService.save(any()) }
    }

    @Test
    fun `opprett TriggSvalbardtilleggbehandlingIBaSakTask hvis person flytter til Svalbard`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse } returns
            listOf(Oppholdsadresse(gyldigFraOgMed = LocalDate.of(2025, 1, 1), oppholdAnnetSted = OppholdAnnetSted.PAA_SVALBARD.name))

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        val taskSlot = slot<Task>()
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 1) { mockTaskService.save(capture(taskSlot)) }

        assertThat(taskSlot.captured.payload).isEqualTo(personIdent)
        assertThat(taskSlot.captured.type).isEqualTo(TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE)
    }

    @Test
    fun `opprett TriggSvalbardtilleggbehandlingIBaSakTask hvis person flytter fra Svalbard`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse } returns
            listOf(Oppholdsadresse(gyldigFraOgMed = LocalDate.of(2025, 1, 1), gyldigTilOgMed = LocalDate.of(2025, 8, 31), oppholdAnnetSted = OppholdAnnetSted.PAA_SVALBARD.name))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns listOf(mockk())

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        val taskSlot = slot<Task>()
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 1) { mockTaskService.finnTaskMedPayloadOgType(personIdent, TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 1) { mockTaskService.save(capture(taskSlot)) }

        assertThat(taskSlot.captured.payload).isEqualTo(personIdent)
        assertThat(taskSlot.captured.type).isEqualTo(TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE)
    }

    @Test
    fun `opprett TriggSvalbardtilleggbehandlingIBaSakTask med riktig triggertid i prod`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse } returns
            listOf(Oppholdsadresse(gyldigFraOgMed = LocalDate.of(2025, 1, 1), gyldigTilOgMed = LocalDate.of(2025, 8, 31), oppholdAnnetSted = OppholdAnnetSted.PAA_SVALBARD.name))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns listOf(mockk())
        every { mockEnvironment.activeProfiles } returns arrayOf("prod")

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        val taskSlot = slot<Task>()
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 1) { mockTaskService.finnTaskMedPayloadOgType(personIdent, TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 1) { mockTaskService.save(capture(taskSlot)) }

        assertThat(taskSlot.captured.payload).isEqualTo(personIdent)
        assertThat(taskSlot.captured.type).isEqualTo(TriggSvalbardtilleggbehandlingIBaSakTask.TASK_STEP_TYPE)
        assertThat(taskSlot.captured.triggerTid).isAfterOrEqualTo(LocalDateTime.of(2025, 11, 1, 0, 0))
    }
}
