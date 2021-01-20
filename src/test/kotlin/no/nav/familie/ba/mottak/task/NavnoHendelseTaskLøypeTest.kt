package no.nav.familie.ba.mottak.task

import io.mockk.*
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.ba.mottak.integrasjoner.Opphørsgrunn.MIGRERT
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpServerErrorException


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NavnoHendelseTaskLøypeTest {

    private val mockJournalpostClient: JournalpostClient = mockk()
    private val mockDokarkivClient: DokarkivClient = mockk(relaxed = true)
    private val mockSakClient: SakClient = mockk()
    private val mockAktørClient: AktørClient = mockk()
    private val mockTaskRepository: TaskRepository = mockk(relaxed = true)
    private val mockPdlClient: PdlClient = mockk(relaxed = true)
    private val mockInfotrygdBarnetrygdClient: InfotrygdBarnetrygdClient = mockk()

    private val rutingSteg = JournalhendelseRutingTask(mockPdlClient,
                                                       mockSakClient,
                                                       mockInfotrygdBarnetrygdClient,
                                                       mockTaskRepository)

    private val journalføringSteg = OppdaterOgFerdigstillJournalpostTask(mockJournalpostClient,
                                                                         mockDokarkivClient,
                                                                         mockSakClient,
                                                                         mockAktørClient,
                                                                         mockTaskRepository)


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
                              kanal = MOTTAK_KANAL,
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

        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse()

        every {
            mockInfotrygdBarnetrygdClient.hentLøpendeUtbetalinger(any(), any())
        } returns InfotrygdSøkResponse(emptyList(), emptyList())

        every {
            mockInfotrygdBarnetrygdClient.hentSaker(any(), any())
        } returns InfotrygdSøkResponse(emptyList(), emptyList())
    }

    @Test
    fun `Skal oppdatere og ferdigstille journalpost og deretter lagre ny OpprettBehandleSakOppgaveTask`() {
        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse(baSak = Sakspart.SØKER)

        every { mockSakClient.hentSaksnummer(any()) } returns FAGSAK_ID

        journalføringSteg.doTask(Task.nyTask(OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE, payload = "mockJournalpostId"))

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

        rutingSteg.doTask(Task.nyTask(type = JournalhendelseRutingTask.TASK_STEP_TYPE,
                                      payload = MOTTAK_KANAL).apply {
            this.metadata["personIdent"] = "12345678901"
            this.metadata["journalpostId"] = "mockJournalpostId"
        })

        verify(exactly = 0) {
            mockTaskRepository.save(any())
        }
    }

    @Test
    fun `Skal returnere uten å journalføre når bruker har sak i Infotrygd`() {
        every {
            mockInfotrygdBarnetrygdClient.hentLøpendeUtbetalinger(any(), any())
        } returns InfotrygdSøkResponse(listOf(StønadDto(1)), emptyList())

        rutingSteg.doTask(Task.nyTask(type = JournalhendelseRutingTask.TASK_STEP_TYPE,
                                      payload = MOTTAK_KANAL).apply {
            this.metadata["personIdent"] = "12345678901"
            this.metadata["journalpostId"] = "mockJournalpostId"
        })

        verify(exactly = 0) {
            mockTaskRepository.save(any())
        }
    }

    @Test
    fun `Skal droppe automatisk journalføring og lagre ny OpprettJournalføringOppgaveTask hvis bruker har sak begge steder`() {
        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse(baSak = Sakspart.SØKER)

        every { mockSakClient.hentSaksnummer(any()) } returns FAGSAK_ID

        every {
            mockInfotrygdBarnetrygdClient.hentLøpendeUtbetalinger(any(), any())
        } returns InfotrygdSøkResponse(listOf(StønadDto(1)), listOf(StønadDto(2)))

        val task = kjørRutingTaskOgReturnerNesteTask()

        Assertions.assertThat(task.taskStepType).isEqualTo(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE)
    }

    @Test
    fun `Skal automatisk journalføre mot ny løsning når bruker er migrert fra Infotrygd`() {
        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse(baSak = Sakspart.SØKER)

        every { mockSakClient.hentSaksnummer(any()) } returns FAGSAK_ID

        every {
            mockInfotrygdBarnetrygdClient.hentSaker(any(), any())
        } returns InfotrygdSøkResponse(listOf(SakDto(status = StatusKode.FB.name,
                                                     stønadList = listOf(StønadDto(1, opphørsgrunn = MIGRERT.kode)))),
                                       emptyList())

        kjørRutingTaskOgReturnerNesteTask().run { journalføringSteg.doTask(this) }

        verify(exactly = 1) {
            mockDokarkivClient.oppdaterJournalpostSak(any(), FAGSAK_ID)
            mockDokarkivClient.ferdigstillJournalpost(JOURNALPOST_ID)
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

        journalføringSteg.doTask(Task.nyTask(OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE, payload = "mockJournalpostId"))

        val task = slot<Task>()

        verify(exactly = 1) {
            mockTaskRepository.save(capture(task))
        }
        Assertions.assertThat(task.captured.taskStepType).isEqualTo(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE)
    }

    private fun kjørRutingTaskOgReturnerNesteTask(): Task {
        rutingSteg.doTask(Task.nyTask(type = JournalhendelseRutingTask.TASK_STEP_TYPE,
                                      payload = MOTTAK_KANAL).apply {
            this.metadata["personIdent"] = "12345678901"
            this.metadata["journalpostId"] = "mockJournalpostId"
        })

        val nesteTask = slot<Task>().let { nyTask ->
            verify(atMost = 1) {
                mockTaskRepository.save(capture(nyTask))
            }
            nyTask.captured
        }
        return nesteTask
    }


    companion object {
        private const val JOURNALPOST_ID = "222"
        private const val FAGSAK_ID = "1"
        private const val MOTTAK_KANAL = "NAV_NO"
    }

}