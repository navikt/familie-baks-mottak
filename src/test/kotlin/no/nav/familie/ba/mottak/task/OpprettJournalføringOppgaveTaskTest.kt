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

    private val taskStep = OpprettJournalføringOppgaveTask(mockJournalpostClient,
                                                           mockOppgaveClient,
                                                           mockSakClient,
                                                           mockAktørClient,
                                                           mockTaskRepository)


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
    fun `Oppretter oppgave med beskrivelse "Må løses i BA-sak" hvis bruker på journalpost har sak der fra før`() {
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockSakClient.hentPågåendeSakStatus(any())
        } returns RestPågåendeSakSøk(harPågåendeSakIBaSak = true,
                                     harPågåendeSakIInfotrygd = false)

        taskStep.doTask(Task.nyTask(TASK_STEP_TYPE, payload = "mockJournalpostId"))

        Assertions.assertThat(sakssystemMarkering.captured).contains("Må løses i BA-sak")
    }

    @Test
    fun `Oppretter oppgave med beskrivelse "Må løses i Gosys" hvis bruker har sak i Infotrygd fra før`() {
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockSakClient.hentPågåendeSakStatus(any())
        } returns RestPågåendeSakSøk(harPågåendeSakIBaSak = false,
                                     harPågåendeSakIInfotrygd = true)

        taskStep.doTask(Task.nyTask(TASK_STEP_TYPE, payload = "mockJournalpostId"))

        Assertions.assertThat(sakssystemMarkering.captured).contains("Må løses i Gosys")
    }

    @Test
    fun `Oppretter oppgave uten markering av sakssytem når bruker ikke har sak fra før`() {
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any())
        } returns OppgaveResponse(1)

        every {
            mockSakClient.hentPågåendeSakStatus(any())
        } returns RestPågåendeSakSøk(harPågåendeSakIBaSak = false,
                                     harPågåendeSakIInfotrygd = false)

        taskStep.doTask(Task.nyTask(TASK_STEP_TYPE, payload = "mockJournalpostId"))

        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), beskrivelse = null)
        }
    }
}