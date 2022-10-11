package no.nav.familie.baks.mottak.task

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.hendelser.JournalføringHendelseServiceTest
import no.nav.familie.baks.mottak.integrasjoner.Bruker
import no.nav.familie.baks.mottak.integrasjoner.BrukerIdType
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle.BARN
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus.LØPENDE
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdBarnetrygdClient
import no.nav.familie.baks.mottak.integrasjoner.Journalpost
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.Journalposttype
import no.nav.familie.baks.mottak.integrasjoner.Journalstatus
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.baks.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkResponse
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import no.nav.familie.kontrakter.ba.infotrygd.Sak as SakDto
import no.nav.familie.kontrakter.ba.infotrygd.Stønad as StønadDto

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkanHendelseTaskLøypeTest {

    private val mockJournalpostClient: JournalpostClient = mockk()
    private val mockOppgaveClient: OppgaveClient = mockk()
    private val mockSakClient: SakClient = mockk()
    private val mockTaskRepository: TaskRepository = mockk(relaxed = true)
    private val mockPdlClient: PdlClient = mockk(relaxed = true)
    private val mockInfotrygdBarnetrygdClient: InfotrygdBarnetrygdClient = mockk()

    private val rutingSteg = JournalhendelseRutingTask(
        mockPdlClient,
        mockSakClient,
        mockInfotrygdBarnetrygdClient,
        mockTaskRepository
    )

    private val journalføringSteg = OpprettJournalføringOppgaveTask(
        mockJournalpostClient,
        mockOppgaveClient
    )

    @BeforeEach
    internal fun setUp() {
        clearAllMocks()
        // Inngående papirsøknad, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(any())
        } returns Journalpost(
            journalpostId = JournalføringHendelseServiceTest.JOURNALPOST_PAPIRSØKNAD,
            journalposttype = Journalposttype.I,
            journalstatus = Journalstatus.MOTTATT,
            bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
            tema = "BAR",
            kanal = MOTTAK_KANAL,
            behandlingstema = null,
            dokumenter = null,
            journalforendeEnhet = null,
            sak = null
        )

        every {
            mockOppgaveClient.finnOppgaver(any(), any())
        } returns listOf()

        every {
            mockTaskRepository.save(any<Task>())
        } returns null

        every {
            mockPdlClient.hentPersonident(any())
        } returns "12345678910"

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
    fun `Oppretter oppgave med beskrivelse som sier at bruker på journalpost har sak i ba-sak`() {
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns listOf(RestFagsakDeltager("12345678910", FORELDER, 1L, LØPENDE))

        kjørRutingTaskOgReturnerNesteTask().run {
            journalføringSteg.doTask(this)
        }
        assertThat(sakssystemMarkering.captured).contains("Bruker har sak i BA-sak")
    }

    @Test
    fun `Oppretter oppgave med beskrivelse som sier at søsken har sak i ba-sak`() {
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns listOf(RestFagsakDeltager("12345678910", BARN, 1L, LØPENDE))

        kjørRutingTaskOgReturnerNesteTask().run {
            journalføringSteg.doTask(this)
        }
        assertThat(sakssystemMarkering.captured).contains("Søsken har sak i BA-sak")
    }

    @Test
    fun `Oppretter oppgave med beskrivelse som sier at bruker har sak i Infotrygd`() {
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockInfotrygdBarnetrygdClient.hentLøpendeUtbetalinger(any(), any())
        } returns InfotrygdSøkResponse(listOf(StønadDto()), listOf(StønadDto()))

        kjørRutingTaskOgReturnerNesteTask().run {
            journalføringSteg.doTask(this)
        }
        assertThat(sakssystemMarkering.captured).contains("Bruker har sak i Infotrygd")
    }

    @Test
    fun `Oppretter oppgave med beskrivelse som sier at søsken har sak i Infotrygd`() {
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockInfotrygdBarnetrygdClient.hentSaker(any(), any())
        } returns InfotrygdSøkResponse(emptyList(), listOf(SakDto(status = "UB")))

        kjørRutingTaskOgReturnerNesteTask().run {
            journalføringSteg.doTask(this)
        }
        assertThat(sakssystemMarkering.captured).contains("Søsken har sak i Infotrygd")
    }

    @Test
    fun `Oppretter oppgave uten markering av sakssystem når bruker ikke har sak fra før`() {
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any())
        } returns OppgaveResponse(1)

        kjørRutingTaskOgReturnerNesteTask().run {
            journalføringSteg.doTask(this)
        }
        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), beskrivelse = null)
        }
    }

    @Test
    fun `Oppretter oppgave uten markering av sakssystem når journalpost mangler bruker`() {
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any())
        } returns OppgaveResponse(1)

        kjørRutingTaskOgReturnerNesteTask(brukerId = null).run {
            journalføringSteg.doTask(this)
        }
        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), beskrivelse = null)
        }
    }

    private fun kjørRutingTaskOgReturnerNesteTask(brukerId: String? = "12345678901"): Task {
        rutingSteg.doTask(
            Task(
                type = JournalhendelseRutingTask.TASK_STEP_TYPE,
                payload = MOTTAK_KANAL
            ).apply {
                if (brukerId != null) {
                    this.metadata["personIdent"] = brukerId
                }
                this.metadata["journalpostId"] = "mockJournalpostId"
            }
        )

        val nesteTask = slot<Task>().let { nyTask ->
            verify(exactly = 1) {
                mockTaskRepository.save(capture(nyTask))
            }
            nyTask.captured
        }
        return nesteTask
    }

    companion object {

        private val MOTTAK_KANAL = "SKAN_NETS"
    }
}
