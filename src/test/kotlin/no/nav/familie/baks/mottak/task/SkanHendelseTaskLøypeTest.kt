package no.nav.familie.baks.mottak.task

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.hendelser.JournalføringHendelseServiceTest
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle.BARN
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus.LØPENDE
import no.nav.familie.baks.mottak.integrasjoner.IdentInformasjon
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdBarnetrygdClient
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.baks.mottak.journalføring.AutomatiskJournalføringBarnetrygdService
import no.nav.familie.baks.mottak.journalføring.JournalpostBrukerService
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkResponse
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
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
    private val mockSakClient: BaSakClient = mockk()
    private val mockTaskService: TaskService = mockk(relaxed = true)
    private val mockPdlClient: PdlClient = mockk(relaxed = true)
    private val mockInfotrygdBarnetrygdClient: InfotrygdBarnetrygdClient = mockk()
    private val mockUnleashNextMedContextService: UnleashNextMedContextService = mockk()
    private val mockAutomatiskJournalføringBarnetrygdService: AutomatiskJournalføringBarnetrygdService = mockk()
    private val mockJournalpostBrukerService: JournalpostBrukerService = mockk()

    private val rutingSteg =
        JournalhendelseBarnetrygdRutingTask(
            pdlClient = mockPdlClient,
            baSakClient = mockSakClient,
            infotrygdBarnetrygdClient = mockInfotrygdBarnetrygdClient,
            taskService = mockTaskService,
            journalpostClient = mockJournalpostClient,
            unleashNextMedContextService = mockUnleashNextMedContextService,
            automatiskJournalføringBarnetrygdService = mockAutomatiskJournalføringBarnetrygdService,
            journalpostBrukerService = mockJournalpostBrukerService,
        )

    private val journalføringSteg =
        OpprettJournalføringOppgaveTask(
            mockJournalpostClient,
            mockOppgaveClient,
        )

    @BeforeEach
    internal fun setUp() {
        clearAllMocks()
        // Inngående papirsøknad, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(any())
        } returns
            Journalpost(
                journalpostId = JournalføringHendelseServiceTest.JOURNALPOST_PAPIRSØKNAD,
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                tema = "BAR",
                kanal = MOTTAK_KANAL,
                behandlingstema = null,
                dokumenter = null,
                journalforendeEnhet = null,
                sak = null,
            )

        every {
            mockOppgaveClient.finnOppgaver(any(), any())
        } returns listOf()

        every {
            mockTaskService.save(any<Task>())
        } returns Task("dummy", "payload")

        every {
            mockPdlClient.hentIdenter(any(), any())
        } returns listOf(IdentInformasjon("12345678910", historisk = false, gruppe = "FOLKEREGISTERIDENT"))

        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns emptyList()

        every {
            mockInfotrygdBarnetrygdClient.hentLøpendeUtbetalinger(any(), any())
        } returns InfotrygdSøkResponse(emptyList(), emptyList())

        every {
            mockInfotrygdBarnetrygdClient.hentSaker(any(), any())
        } returns InfotrygdSøkResponse(emptyList(), emptyList())

        every {
            mockUnleashNextMedContextService.isEnabled(any(), any())
        } returns false

        every { mockJournalpostBrukerService.tilPersonIdent(any(), any()) } returns "12345678910"
    }

    @Test
    fun `Oppretter oppgave med beskrivelse som sier at bruker på journalpost har sak i ba-sak`() {
        // Arrange
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns listOf(RestFagsakDeltager("12345678910", FORELDER, 1L, LØPENDE))

        every {
            mockSakClient.hentFagsaknummerPåPersonident(any())
        } returns 1L

        every {
            mockAutomatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(any(), any())
        } returns false

        // Act
        kjørRutingTaskOgReturnerNesteTask().run {
            journalføringSteg.doTask(this)
        }

        // Assert
        assertThat(sakssystemMarkering.captured).contains("Bruker har sak i BA-sak")
    }

    @Test
    fun `Oppretter oppgave med beskrivelse som sier at søsken har sak i ba-sak`() {
        // Arrange
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns listOf(RestFagsakDeltager("12345678910", BARN, 1L, LØPENDE))

        every {
            mockAutomatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(any(), any())
        } returns false

        every {
            mockSakClient.hentFagsaknummerPåPersonident(any())
        } returns 1L

        // Act
        kjørRutingTaskOgReturnerNesteTask().run {
            journalføringSteg.doTask(this)
        }

        // Assert
        assertThat(sakssystemMarkering.captured).contains("Søsken har sak i BA-sak")
    }

    @Test
    fun `Oppretter oppgave med beskrivelse som sier at bruker har sak i Infotrygd`() {
        // Arrange
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockInfotrygdBarnetrygdClient.hentLøpendeUtbetalinger(any(), any())
        } returns InfotrygdSøkResponse(listOf(StønadDto()), listOf(StønadDto()))

        every {
            mockAutomatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(any(), any())
        } returns false

        every {
            mockSakClient.hentFagsaknummerPåPersonident(any())
        } returns 1L

        // Act
        kjørRutingTaskOgReturnerNesteTask().run {
            journalføringSteg.doTask(this)
        }

        // Assert
        assertThat(sakssystemMarkering.captured).contains("Bruker har sak i Infotrygd")
    }

    @Test
    fun `Oppretter oppgave med beskrivelse som sier at søsken har sak i Infotrygd`() {
        // Arrange
        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        every {
            mockInfotrygdBarnetrygdClient.hentSaker(any(), any())
        } returns InfotrygdSøkResponse(emptyList(), listOf(SakDto(status = "UB")))

        every {
            mockAutomatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(any(), any())
        } returns false

        every {
            mockSakClient.hentFagsaknummerPåPersonident(any())
        } returns 1L

        // Act
        kjørRutingTaskOgReturnerNesteTask().run {
            journalføringSteg.doTask(this)
        }

        // Assert
        assertThat(sakssystemMarkering.captured).contains("Søsken har sak i Infotrygd")
    }

    @Test
    fun `Oppretter oppgave uten markering av sakssystem når bruker ikke har sak fra før`() {
        // Arrange
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any())
        } returns OppgaveResponse(1)

        every {
            mockAutomatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(any(), any())
        } returns false

        every {
            mockSakClient.hentFagsaknummerPåPersonident(any())
        } returns 1L

        // Act
        kjørRutingTaskOgReturnerNesteTask().run {
            journalføringSteg.doTask(this)
        }

        // Assert
        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), beskrivelse = null)
        }
    }

    @Test
    fun `Oppretter oppgave uten markering av sakssystem når journalpost mangler bruker`() {
        // Arrange
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any())
        } returns OppgaveResponse(1)

        every {
            mockAutomatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(any(), any())
        } returns false

        every {
            mockSakClient.hentFagsaknummerPåPersonident(any())
        } returns 1L

        // Act
        kjørRutingTaskOgReturnerNesteTask(brukerId = null).run {
            journalføringSteg.doTask(this)
        }

        // Assert
        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), beskrivelse = null)
        }
    }

    private fun kjørRutingTaskOgReturnerNesteTask(brukerId: String? = "12345678901"): Task {
        rutingSteg.doTask(
            Task(
                type = JournalhendelseBarnetrygdRutingTask.TASK_STEP_TYPE,
                payload = MOTTAK_KANAL,
            ).apply {
                if (brukerId != null) {
                    this.metadata["personIdent"] = brukerId
                }
                this.metadata["journalpostId"] = "mockJournalpostId"
            },
        )

        val nesteTask =
            slot<Task>().let { nyTask ->
                verify(exactly = 1) {
                    mockTaskService.save(capture(nyTask))
                }
                nyTask.captured
            }
        return nesteTask
    }

    companion object {
        private val MOTTAK_KANAL = "SKAN_NETS"
    }
}
