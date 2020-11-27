package no.nav.familie.ba.mottak.task

import io.mockk.*
import no.nav.familie.ba.mottak.hendelser.JournalføringHendelseServiceTest
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.ba.mottak.task.OpprettJournalføringOppgaveTask.Companion.TASK_STEP_TYPE
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpprettJournalføringOppgaveTaskTest {

    private val mockJournalpostClient: JournalpostClient = mockk()
    private val mockOppgaveClient: OppgaveClient = mockk()
    private val mockSakClient: SakClient = mockk()
    private val mockAktørClient: AktørClient = mockk()
    private val mockTaskRepository: TaskRepository = mockk(relaxed = true)
    private val mockPersonClient: PersonClient = mockk(relaxed = true)

    private val taskStep = OpprettJournalføringOppgaveTask(mockJournalpostClient,
                                                           mockOppgaveClient,
                                                           mockSakClient,
                                                           mockAktørClient,
                                                           mockTaskRepository,
                                                           mockPersonClient)


    @BeforeAll
    internal fun setUp() {
        //Inngående papirsøknad, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(any())
        } returns Journalpost(journalpostId = JournalføringHendelseServiceTest.JOURNALPOST_PAPIRSØKNAD,
                              journalposttype = Journalposttype.I,
                              journalstatus = Journalstatus.MOTTATT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "BAR",
                              kanal = "SKAN_NETS",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null)

        every {
            mockOppgaveClient.finnOppgaver(any(), any())
        } returns listOf()

        every {
            mockTaskRepository.saveAndFlush(any<Task>())
        } returns null

        every {
            mockAktørClient.hentPersonident(any())
        } returns "12345678910"

    }

    @Test
    fun `Oppretter oppgave med beskrivelse som sier at bruker på journalpost har sak i ba-sak`() {
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse(baSak = Sakspart.SØKER)

        taskStep.doTask(Task.nyTask(TASK_STEP_TYPE, payload = "mockJournalpostId"))

        Assertions.assertThat(sakssystemMarkering.captured).contains("Bruker har sak i BA-sak")
    }

    @Test
    fun `Oppretter oppgave med beskrivelse som sier at søsken har sak i ba-sak`() {
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse(baSak = Sakspart.ANNEN)

        taskStep.doTask(Task.nyTask(TASK_STEP_TYPE, payload = "mockJournalpostId"))

        Assertions.assertThat(sakssystemMarkering.captured).contains("Søsken har sak i BA-sak")
    }

    @Test
    fun `Oppretter oppgave med beskrivelse som sier at bruker har sak i Infotrygd`() {
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse(infotrygd = Sakspart.SØKER)

        taskStep.doTask(Task.nyTask(TASK_STEP_TYPE, payload = "mockJournalpostId"))

        Assertions.assertThat(sakssystemMarkering.captured).contains("Bruker har sak i Infotrygd")
    }

    @Test
    fun `Oppretter oppgave med beskrivelse som sier at søsken har sak i Infotrygd`() {
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse(infotrygd = Sakspart.ANNEN)

        taskStep.doTask(Task.nyTask(TASK_STEP_TYPE, payload = "mockJournalpostId"))

        Assertions.assertThat(sakssystemMarkering.captured).contains("Søsken har sak i Infotrygd")
    }

    @Test
    fun `Oppretter oppgave uten markering av sakssytem når bruker ikke har sak fra før`() {
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any())
        } returns OppgaveResponse(1)

        every {
            mockSakClient.hentPågåendeSakStatus(any(), emptyList())
        } returns RestPågåendeSakResponse()

        taskStep.doTask(Task.nyTask(TASK_STEP_TYPE, payload = "mockJournalpostId"))

        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), beskrivelse = null)
        }
    }
}