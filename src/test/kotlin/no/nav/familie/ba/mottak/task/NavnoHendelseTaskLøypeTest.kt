package no.nav.familie.ba.mottak.task

import io.mockk.*
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.ba.mottak.integrasjoner.Opphørsgrunn.MIGRERT
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkResponse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import no.nav.familie.kontrakter.ba.infotrygd.Sak as SakDto
import no.nav.familie.kontrakter.ba.infotrygd.Stønad as StønadDto

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NavnoHendelseTaskLøypeTest {

    private val mockJournalpostClient: JournalpostClient = mockk()
    private val mockSakClient: SakClient = mockk()
    private val mockOppgaveClient: OppgaveClient = mockk(relaxed = true)
    private val mockTaskRepository: TaskRepository = mockk(relaxed = true)
    private val mockPdlClient: PdlClient = mockk(relaxed = true)
    private val mockInfotrygdBarnetrygdClient: InfotrygdBarnetrygdClient = mockk()

    private val rutingSteg = JournalhendelseRutingTask(mockPdlClient,
                                                       mockSakClient,
                                                       mockInfotrygdBarnetrygdClient,
                                                       mockTaskRepository)

    private val journalføringSteg = OpprettJournalføringOppgaveTask(mockJournalpostClient,
                                                                    mockOppgaveClient,
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
            mockOppgaveClient.finnOppgaver(any(), any())
        } returns listOf()

        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns emptyList()

        every {
            mockInfotrygdBarnetrygdClient.hentLøpendeUtbetalinger(any(), any())
        } returns InfotrygdSøkResponse(emptyList(), emptyList())

        every {
            mockInfotrygdBarnetrygdClient.hentSaker(any(), any())
        } returns InfotrygdSøkResponse(emptyList(), emptyList())
    }

    @Test
    fun `Skal opprette JFR-oppgave med tekst om at bruker har sak i BA-sak`() {
        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns listOf(RestFagsakDeltager("12345678901", FagsakDeltagerRolle.FORELDER, FAGSAK_ID.toLong()))

        every { mockSakClient.hentSaksnummer(any()) } returns FAGSAK_ID

        kjørRutingTaskOgReturnerNesteTask().run {
            Assertions.assertThat(this.taskStepType).isEqualTo(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE)
            journalføringSteg.doTask(this)
        }

        val oppgaveBeskrivelse = slot<String>()

        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(oppgaveBeskrivelse))
        }
        Assertions.assertThat(oppgaveBeskrivelse.captured).isEqualTo("Bruker har sak i BA-sak")
    }

    @Test
    fun `Skal ikke gå videre når bruker ikke har sak i BA-sak`() {
        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns emptyList()

        Assertions.assertThatThrownBy {
            kjørRutingTaskOgReturnerNesteTask()
        }.hasMessageContainingAll("TaskRepository", ".save", "was not called")
    }

    @Test
    fun `Skal ikke gå videre når bruker har sak i Infotrygd`() {
        every {
            mockInfotrygdBarnetrygdClient.hentLøpendeUtbetalinger(any(), any())
        } returns InfotrygdSøkResponse(listOf(StønadDto()), emptyList())

        Assertions.assertThatThrownBy {
            kjørRutingTaskOgReturnerNesteTask()
        }.hasMessageContainingAll("TaskRepository", ".save", "was not called")
    }

    @Test
    fun `Skal opprette JFR-oppgave med tekst om at bruker har sak begge steder`() {
        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns listOf(RestFagsakDeltager("12345678901", FagsakDeltagerRolle.FORELDER, FAGSAK_ID.toLong()))

        every { mockSakClient.hentSaksnummer(any()) } returns FAGSAK_ID

        every {
            mockInfotrygdBarnetrygdClient.hentLøpendeUtbetalinger(any(), any())
        } returns InfotrygdSøkResponse(listOf(StønadDto()), listOf(StønadDto()))

        kjørRutingTaskOgReturnerNesteTask().run { journalføringSteg.doTask(this) }

        val oppgaveBeskrivelse = slot<String>()

        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(oppgaveBeskrivelse))
        }
        Assertions.assertThat(oppgaveBeskrivelse.captured).isEqualTo("Bruker har sak i både Infotrygd og BA-sak")
    }

    @Test
    fun `Skal opprette JFR-oppgave med henvisning til BA-sak når bruker er migrert fra Infotrygd`() {
        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns listOf(RestFagsakDeltager("12345678901", FagsakDeltagerRolle.FORELDER, FAGSAK_ID.toLong()))

        every { mockSakClient.hentSaksnummer(any()) } returns FAGSAK_ID

        every {
            mockInfotrygdBarnetrygdClient.hentSaker(any(), any())
        } returns InfotrygdSøkResponse(listOf(SakDto(status = StatusKode.FB.name,
                                                     vedtaksdato = LocalDate.now(),
                                                     stønad = StønadDto(opphørsgrunn = MIGRERT.kode))),
                                       emptyList())

        kjørRutingTaskOgReturnerNesteTask().run { journalføringSteg.doTask(this) }

        val oppgaveBeskrivelse = slot<String>()

        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(oppgaveBeskrivelse))
        }
        Assertions.assertThat(oppgaveBeskrivelse.captured).isEqualTo("Bruker har sak i BA-sak")
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