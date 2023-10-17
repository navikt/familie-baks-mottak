package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import no.nav.familie.baks.mottak.hendelser.JournalføringHendelseServiceTest
import no.nav.familie.baks.mottak.integrasjoner.Bruker
import no.nav.familie.baks.mottak.integrasjoner.BrukerIdType
import no.nav.familie.baks.mottak.integrasjoner.DokumentInfo
import no.nav.familie.baks.mottak.integrasjoner.Dokumentstatus
import no.nav.familie.baks.mottak.integrasjoner.Journalpost
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.Journalposttype
import no.nav.familie.baks.mottak.integrasjoner.Journalstatus
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClient
import no.nav.familie.baks.mottak.task.OpprettJournalføringOppgaveTask.Companion.TASK_STEP_TYPE
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpprettBehandleSakOppgaveTaskTest {
    private val mockJournalpostClient: JournalpostClient = mockk()
    private val mockOppgaveClient: OppgaveClient = mockk()
    private val mockTaskService: TaskService = mockk(relaxed = true)

    private val taskStep = OpprettBehandleSakOppgaveTask(mockJournalpostClient, mockOppgaveClient)

    @BeforeAll
    internal fun setUp() {
        every {
            mockOppgaveClient.finnOppgaver(any(), any())
        } returns listOf()

        justRun {
            mockTaskService.save(any())
        }
    }

    @Test
    fun `Skal kaste feil hvis journalpost ikke er journalført`() {
        every {
            mockJournalpostClient.hentJournalpost(any())
        } returns lagTestJournalpost(Journalstatus.MOTTATT)

        assertThatThrownBy {
            taskStep.doTask(Task(type = TASK_STEP_TYPE, payload = "mockJournalpostId"))
        }.hasMessage("Kan ikke opprette oppgave før tilhørende journalpost 111 er ferdigstilt")
    }

    @Test
    fun `Skal kaste feil hvis det alt er en åpen oppgave`() {
        every {
            mockJournalpostClient.hentJournalpost(any())
        } returns lagTestJournalpost(Journalstatus.JOURNALFOERT)

        every {
            mockOppgaveClient.finnOppgaver(JournalføringHendelseServiceTest.JOURNALPOST_PAPIRSØKNAD, null)
        } returns listOf(Oppgave())

        assertThatThrownBy {
            taskStep.doTask(Task(type = TASK_STEP_TYPE, payload = "mockJournalpostId"))
        }.hasMessage("Det eksister minst 1 åpen oppgave på journalpost mockJournalpostId")
    }

    @Test
    fun `Skal opprette behandle sak oppgave og beskrivelse lik tittel på hoveddokument`() {
        val journalpost = lagTestJournalpost(Journalstatus.JOURNALFOERT)
        every {
            mockJournalpostClient.hentJournalpost(any())
        } returns journalpost

        every {
            mockOppgaveClient.finnOppgaver(JournalføringHendelseServiceTest.JOURNALPOST_PAPIRSØKNAD, null)
        } returns emptyList()

        every {
            mockOppgaveClient.opprettBehandleSakOppgave(journalpost, "Tittel")
        } returns OppgaveResponse(123)

        val task = Task(type = TASK_STEP_TYPE, payload = "mockJournalpostId")
        taskStep.doTask(task)

        assertThat(task.metadata["oppgaveId"]).isEqualTo("123")
    }

    @Test
    fun `Skal opprette behandle sak oppgave, beskrivelse lik tittel på hoveddokument og markere at den skal behandles i fagsystem BA-sak`() {
        val journalpost = lagTestJournalpost(Journalstatus.JOURNALFOERT)
        every {
            mockJournalpostClient.hentJournalpost(any())
        } returns journalpost

        every {
            mockOppgaveClient.finnOppgaver(JournalføringHendelseServiceTest.JOURNALPOST_PAPIRSØKNAD, null)
        } returns emptyList()

        every {
            mockOppgaveClient.opprettBehandleSakOppgave(journalpost, "Må behandles i BA-sak.\n Tittel")
        } returns OppgaveResponse(321)

        val task = Task(type = TASK_STEP_TYPE, payload = "mockJournalpostId")
        task.metadata["fagsystem"] = "BA"
        taskStep.doTask(task)

        assertThat(task.metadata["oppgaveId"]).isEqualTo("321")
    }

    private fun lagTestJournalpost(status: Journalstatus) =
        Journalpost(
            journalpostId = JournalføringHendelseServiceTest.JOURNALPOST_PAPIRSØKNAD,
            journalposttype = Journalposttype.I,
            journalstatus = status,
            bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
            tema = "BAR",
            kanal = "NAV_NO",
            behandlingstema = null,
            dokumenter =
                listOf(
                    DokumentInfo(
                        "Tittel",
                        "",
                        Dokumentstatus.FERDIGSTILT,
                        emptyList(),
                    ),
                ),
            journalforendeEnhet = null,
            sak = null,
        )
}
