package no.nav.familie.ba.mottak.task

import io.mockk.*
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.ba.mottak.task.OppdaterOgFerdigstillJournalpostTask.Companion.TASK_STEP_TYPE
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpServerErrorException


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OppdaterOgFerdigstillJournalpostTaskTest {

    private val mockJournalpostClient: JournalpostClient = mockk()
    private val mockDokarkivClient: DokarkivClient = mockk(relaxed = true)
    private val mockSakClient: SakClient = mockk()
    private val mockAktørClient: AktørClient = mockk()
    private val mockTaskRepository: TaskRepository = mockk(relaxed = true)
    private val mockPersonClient: PersonClient = mockk(relaxed = true)

    private val taskStep = OppdaterOgFerdigstillJournalpostTask(mockJournalpostClient,
                                                                mockDokarkivClient,
                                                                mockSakClient,
                                                                mockAktørClient,
                                                                mockTaskRepository,
                                                                mockPersonClient)


    @BeforeEach
    internal fun setUp() {
        clearAllMocks()
        //Inngående digital, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(any())
        } returns Journalpost(journalpostId = JOURNALPOST_ID,
                              journalposttype = Journalposttype.I,
                              journalstatus = Journalstatus.MOTTATT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "BAR",
                              kanal = "NAV_NO",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null)

        every {
            mockTaskRepository.saveAndFlush(any<Task>())
        } returns null

        every {
            mockAktørClient.hentPersonident(any())
        } returns "12345678910"
    }

    @Test
    fun `Skal oppdatere og ferdigstille journalpost og deretter lagre ny OpprettBehandleSakOppgaveTask`() {
        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse(baSak = Sakspart.SØKER)

        every { mockSakClient.hentSaksnummer(any()) } returns FAGSAK_ID

        taskStep.doTask(Task.nyTask(TASK_STEP_TYPE, payload = "mockJournalpostId"))

        val task = slot<Task>()

        verify(exactly = 1) {
            mockDokarkivClient.oppdaterJournalpostSak(any(), FAGSAK_ID)
            mockDokarkivClient.ferdigstillJournalpost(JOURNALPOST_ID)
            mockTaskRepository.save(capture(task))
        }
        Assertions.assertThat(task.captured.taskStepType).isEqualTo(OpprettBehandleSakOppgaveTask.TASK_STEP_TYPE)
    }

    @Test
    fun `Skal returnere uten å journalføre når bruker ikke har sak i BA-sak`() {
        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse()

        taskStep.doTask(Task.nyTask(TASK_STEP_TYPE, payload = "mockJournalpostId"))

        verify(exactly = 0) {
            mockDokarkivClient.oppdaterJournalpostSak(any(), any())
            mockDokarkivClient.ferdigstillJournalpost(any())
            mockTaskRepository.save(any())
        }
    }

    @Test
    fun `Skal returnere uten å journalføre når bruker har sak i Infotrygd`() {
        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse(infotrygd = Sakspart.SØKER)

        taskStep.doTask(Task.nyTask(TASK_STEP_TYPE, payload = "mockJournalpostId"))

        verify(exactly = 0) {
            mockDokarkivClient.oppdaterJournalpostSak(any(), any())
            mockDokarkivClient.ferdigstillJournalpost(any())
            mockTaskRepository.save(any())
        }
    }

    @Test
    fun `Skal lagre ny OpprettJournalføringOppgaveTask hvis automatisk journalføring feiler`() {
        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse(baSak = Sakspart.SØKER)

        every { mockSakClient.hentSaksnummer(any()) } returns FAGSAK_ID

        every {
            mockDokarkivClient.ferdigstillJournalpost(any())
        } throws (HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))

        taskStep.doTask(Task.nyTask(TASK_STEP_TYPE, payload = "mockJournalpostId"))

        val task = slot<Task>()

        verify(exactly = 1) {
            mockTaskRepository.save(capture(task))
        }
        Assertions.assertThat(task.captured.taskStepType).isEqualTo(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE)
    }

    companion object {
        private const val JOURNALPOST_ID = "222"
        private const val FAGSAK_ID = "1"
    }

}