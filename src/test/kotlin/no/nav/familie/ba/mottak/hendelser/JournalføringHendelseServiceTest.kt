package no.nav.familie.ba.mottak.hendelser

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import no.nav.familie.ba.mottak.config.FeatureToggleService
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.ba.mottak.task.OppdaterOgFerdigstillJournalpostTask
import no.nav.familie.ba.mottak.task.OpprettJournalføringOppgaveTask
import no.nav.familie.ba.mottak.task.SendTilSakTask
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.internal.matchers.Any
import org.slf4j.MDC

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JournalføringHendelseServiceTest {

    @MockK
    lateinit var mockJournalpostClient: JournalpostClient

    @MockK
    lateinit var mockOppgaveClient: OppgaveClient

    @MockK(relaxed = true)
    lateinit var mockTaskRepository: TaskRepository

    @MockK(relaxed = true)
    lateinit var mockFeatureToggleService: FeatureToggleService

    @InjectMockKs
    lateinit var service: JournalhendelseService

    @BeforeEach
    internal fun setUp() {
        MockKAnnotations.init(this)
        clearAllMocks()

        //Inngående papirsøknad, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_PAPIRSØKNAD)
        } returns Journalpost(journalpostId = JOURNALPOST_PAPIRSØKNAD,
                              journalposttype = Journalposttype.I,
                              journalstatus = Journalstatus.MOTTATT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "BAR",
                              kanal = "SKAN_NETS",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null)

        //Inngående digital, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_DIGITALSØKNAD)
        } returns Journalpost(journalpostId = JOURNALPOST_DIGITALSØKNAD,
                              journalposttype = Journalposttype.I,
                              journalstatus = Journalstatus.MOTTATT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "BAR",
                              kanal = "NAV_NO",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null)

        //Utgående digital, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_UTGÅENDE_DOKUMENT)
        } returns Journalpost(journalpostId = JOURNALPOST_UTGÅENDE_DOKUMENT,
                              journalposttype = Journalposttype.U,
                              journalstatus = Journalstatus.MOTTATT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "BAR",
                              kanal = "SKAN_NETS",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null)

        //Ikke barnetrygd
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_IKKE_BARNETRYGD)
        } returns Journalpost(journalpostId = JOURNALPOST_IKKE_BARNETRYGD,
                              journalposttype = Journalposttype.U,
                              journalstatus = Journalstatus.MOTTATT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "FOR",
                              kanal = "NAV_NO",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null)

        //ferdigstilt journalpost
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_FERDIGSTILT)
        } returns Journalpost(journalpostId = JOURNALPOST_FERDIGSTILT,
                              journalposttype = Journalposttype.U,
                              journalstatus = Journalstatus.FERDIGSTILT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "FOR",
                              kanal = "NAV_NO",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null)

        every { mockFeatureToggleService.isEnabled(any()) } returns true
    }

    @Test
    fun `Mottak av papirsøknader skal opprette OpprettOppgaveForJournalføringTask`() {
        MDC.put("callId", "papir")
        val record = opprettRecord(JOURNALPOST_PAPIRSØKNAD)

        service.behandleJournalhendelse(record)


        val taskSlot = slot<Task>()
        verify {
            mockTaskRepository.save(capture(taskSlot))
        }

        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload).isEqualTo(JOURNALPOST_PAPIRSØKNAD)
        assertThat(taskSlot.captured.metadata.getProperty("callId")).isEqualTo("papir")
        assertThat(taskSlot.captured.taskStepType).isEqualTo(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE)
    }


    @Test
    fun `Mottak av digital søknader skal opprette task`() {
        MDC.put("callId", "digital")
        val record = opprettRecord(JOURNALPOST_DIGITALSØKNAD)


        service.behandleJournalhendelse(record)

        val taskSlot = slot<Task>()
        verify {
            mockTaskRepository.save(capture(taskSlot))
        }

        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload).isEqualTo(JOURNALPOST_DIGITALSØKNAD)
        assertThat(taskSlot.captured.metadata.getProperty("callId")).isEqualTo("digital")
        assertThat(taskSlot.captured.taskStepType).isEqualTo(OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE)
    }

    @Test
    fun `Hendelser hvor journalpost ikke har tema for Barnetrygd skal ignoreres`() {
        val record = opprettRecord(JOURNALPOST_IKKE_BARNETRYGD)

        service.behandleJournalhendelse(record)

        verify(exactly = 0) {
            mockTaskRepository.save(any())
        }

    }

    @Test
    fun `Hendelser hvor journalpost er alt FERDIGSTILT skal ignoreres`() {
        val record = opprettRecord(JOURNALPOST_FERDIGSTILT)

        service.behandleJournalhendelse(record)

        verify(exactly = 0) {
            mockTaskRepository.save(any())
        }

    }

    @Test
    fun `Utgående journalposter skal ignoreres`() {
        val record = opprettRecord(JOURNALPOST_UTGÅENDE_DOKUMENT)

        service.behandleJournalhendelse(record)

        verify(exactly = 0) {
            mockTaskRepository.save(any())
        }

    }

    @Test
    fun `Oppretter oppgave dersom det ikke eksisterer en allerede`() {
        every {
            mockOppgaveClient.finnOppgaver(any(), any())
        } returns listOf()

        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any())
        } returns OppgaveResponse(1)

        every {
            mockTaskRepository.saveAndFlush(any<Task>())
        } returns null

        val task = OpprettJournalføringOppgaveTask(
                mockJournalpostClient,
                mockOppgaveClient,
                mockTaskRepository)

        task.doTask(Task.nyTask(SendTilSakTask.TASK_STEP_TYPE, JOURNALPOST_UTGÅENDE_DOKUMENT))

        verify(exactly = 1) {
            mockTaskRepository.saveAndFlush(any<Task>())
        }
    }

    @Test
    fun `Oppretter ikke oppgave dersom det eksisterer en allerede`() {
        every {
            mockOppgaveClient.finnOppgaver(any(), Oppgavetype.Journalføring)
        } returns listOf()
        every {
            mockOppgaveClient.finnOppgaver(JOURNALPOST_UTGÅENDE_DOKUMENT, Oppgavetype.Journalføring)
        } returns listOf(OppgaveDto(123))
        every {
            mockOppgaveClient.finnOppgaver(JOURNALPOST_PAPIRSØKNAD, Oppgavetype.Fordeling)
        } returns listOf(OppgaveDto(123))

        val task = OpprettJournalføringOppgaveTask(
                mockJournalpostClient,
                mockOppgaveClient,
                mockTaskRepository)
        task.doTask(Task.nyTask(SendTilSakTask.TASK_STEP_TYPE, JOURNALPOST_UTGÅENDE_DOKUMENT))
        task.doTask(Task.nyTask(SendTilSakTask.TASK_STEP_TYPE, JOURNALPOST_PAPIRSØKNAD))

        verify(exactly = 0) {
            mockTaskRepository.saveAndFlush(any<Task>())
        }
    }

    @Test
    fun `Kaster exception dersom journalstatus annet enn MOTTATT`() {
        val task = OpprettJournalføringOppgaveTask(
                mockJournalpostClient,
                mockOppgaveClient,
                mockTaskRepository)

        Assertions.assertThrows(IllegalStateException::class.java) {
            task.doTask(Task.nyTask(SendTilSakTask.TASK_STEP_TYPE, JOURNALPOST_FERDIGSTILT))
        }
    }

    private fun opprettRecord(journalpostId: String,
                              hendelseType: String = "MidlertidigJournalført"): JournalfoeringHendelseRecord {
        return JournalfoeringHendelseRecord("hendelseId",
                                            1,
                                            hendelseType,
                                            journalpostId.toLong(),
                                            "M",
                                            "BAR",
                                            "BAR",
                                            "SKAN_NETS",
                                            "kanalReferanseId",
                                            "BAR")
    }

    companion object {
        const val JOURNALPOST_PAPIRSØKNAD = "111"
        const val JOURNALPOST_DIGITALSØKNAD = "222"
        const val JOURNALPOST_UTGÅENDE_DOKUMENT = "333"
        const val JOURNALPOST_IKKE_BARNETRYGD = "444"
        const val JOURNALPOST_FERDIGSTILT = "555"
    }
}