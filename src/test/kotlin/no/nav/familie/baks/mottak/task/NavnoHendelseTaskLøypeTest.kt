package no.nav.familie.baks.mottak.task

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus.LØPENDE
import no.nav.familie.baks.mottak.integrasjoner.IdentInformasjon
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdBarnetrygdClient
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClient
import no.nav.familie.baks.mottak.integrasjoner.Opphørsgrunn.MIGRERT
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.baks.mottak.integrasjoner.StatusKode
import no.nav.familie.baks.mottak.journalføring.AutomatiskJournalføringBarnetrygdService
import no.nav.familie.baks.mottak.journalføring.JournalpostBrukerService
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkResponse
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import no.nav.familie.kontrakter.ba.infotrygd.Sak as SakDto
import no.nav.familie.kontrakter.ba.infotrygd.Stønad as StønadDto

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NavnoHendelseTaskLøypeTest {
    private val mockJournalpostClient: JournalpostClient = mockk()
    private val mockSakClient: BaSakClient = mockk()
    private val mockOppgaveClient: OppgaveClient = mockk(relaxed = true)
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
        // Inngående digital, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(any())
        } returns
            Journalpost(
                journalpostId = JOURNALPOST_ID,
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
            mockTaskService.save(any<Task>())
        } returns Task("dummy", "payload")

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

        every {
            mockPdlClient.hentIdenter(any(), any())
        } returns listOf(IdentInformasjon("12345678910", historisk = false, gruppe = "FOLKEREGISTERIDENT"))
        every {
            mockUnleashNextMedContextService.isEnabled(any(), any())
        } returns false

        every { mockJournalpostBrukerService.tilPersonIdent(any(), any()) } returns "12345678910"
    }

    @Test
    fun `Skal opprette JFR-oppgave med tekst om at bruker har sak i BA-sak`() {
        // Arrange
        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns listOf(RestFagsakDeltager("12345678901", FORELDER, FAGSAK_ID, LØPENDE))

        every { mockSakClient.hentFagsaknummerPåPersonident(any()) } returns FAGSAK_ID

        every { mockAutomatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(any(), any()) } returns false

        // Act
        kjørRutingTaskOgReturnerNesteTask().run {
            assertThat(this.type).isEqualTo(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE)
            journalføringSteg.doTask(this)
        }

        // Assert
        val oppgaveBeskrivelse = slot<String>()

        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(oppgaveBeskrivelse))
        }
        assertThat(oppgaveBeskrivelse.captured).isEqualTo("Bruker har sak i BA-sak")
    }

    @Test
    fun `Skal opprette JFR-oppgave med tekst om at bruker har sak i Infotrygd`() {
        // Arrange
        every {
            mockInfotrygdBarnetrygdClient.hentLøpendeUtbetalinger(any(), any())
        } returns InfotrygdSøkResponse(listOf(StønadDto()), emptyList())

        every { mockSakClient.hentFagsaknummerPåPersonident(any()) } returns FAGSAK_ID

        every { mockAutomatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(any(), any()) } returns false

        // Act
        kjørRutingTaskOgReturnerNesteTask().run {
            assertThat(this.type).isEqualTo(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE)
            journalføringSteg.doTask(this)
        }

        // Assert
        val oppgaveBeskrivelse = slot<String>()

        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(oppgaveBeskrivelse))
        }
        assertThat(oppgaveBeskrivelse.captured).isEqualTo("Bruker har sak i Infotrygd")
    }

    @Test
    fun `Skal opprette JFR-oppgave med tekst om at bruker har sak begge steder`() {
        // Arrange
        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns listOf(RestFagsakDeltager("12345678901", FORELDER, FAGSAK_ID, LØPENDE))

        every { mockSakClient.hentFagsaknummerPåPersonident(any()) } returns FAGSAK_ID

        every {
            mockInfotrygdBarnetrygdClient.hentLøpendeUtbetalinger(any(), any())
        } returns InfotrygdSøkResponse(listOf(StønadDto()), listOf(StønadDto()))

        every { mockAutomatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(any(), any()) } returns false

        // Act
        kjørRutingTaskOgReturnerNesteTask().run { journalføringSteg.doTask(this) }

        // Assert
        val oppgaveBeskrivelse = slot<String>()

        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(oppgaveBeskrivelse))
        }
        assertThat(oppgaveBeskrivelse.captured).isEqualTo("Bruker har sak i både Infotrygd og BA-sak")
    }

    @Test
    fun `Skal opprette JFR-oppgave uten tekst siden bruker ikke har sak i noen system`() {
        // Arrange
        every { mockSakClient.hentFagsaknummerPåPersonident(any()) } returns FAGSAK_ID

        every { mockAutomatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(any(), any()) } returns false

        // Act
        kjørRutingTaskOgReturnerNesteTask().run { journalføringSteg.doTask(this) }

        // Assert
        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), null)
        }
    }

    @Test
    fun `Skal opprette JFR-oppgave med henvisning til BA-sak når bruker er migrert fra Infotrygd`() {
        // Arrange
        every {
            mockSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns listOf(RestFagsakDeltager("12345678901", FORELDER, FAGSAK_ID.toLong(), LØPENDE))

        every { mockSakClient.hentFagsaknummerPåPersonident(any()) } returns FAGSAK_ID

        every {
            mockInfotrygdBarnetrygdClient.hentSaker(any(), any())
        } returns
            InfotrygdSøkResponse(
                listOf(
                    SakDto(
                        status = StatusKode.FB.name,
                        vedtaksdato = LocalDate.now(),
                        stønad = StønadDto(opphørsgrunn = MIGRERT.kode),
                    ),
                ),
                emptyList(),
            )

        every { mockAutomatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(any(), any()) } returns false

        // Act
        kjørRutingTaskOgReturnerNesteTask().run { journalføringSteg.doTask(this) }

        // Assert
        val oppgaveBeskrivelse = slot<String>()

        verify(exactly = 1) {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(oppgaveBeskrivelse))
        }
        assertThat(oppgaveBeskrivelse.captured).isEqualTo("Bruker har sak i BA-sak")
    }

    private fun kjørRutingTaskOgReturnerNesteTask(): Task {
        rutingSteg.doTask(
            Task(
                type = JournalhendelseBarnetrygdRutingTask.TASK_STEP_TYPE,
                payload = MOTTAK_KANAL,
            ).apply {
                this.metadata["personIdent"] = "12345678901"
                this.metadata["journalpostId"] = "mockJournalpostId"
            },
        )

        val nesteTask =
            slot<Task>().let { nyTask ->
                verify(atMost = 1) {
                    mockTaskService.save(capture(nyTask))
                }
                nyTask.captured
            }
        return nesteTask
    }

    companion object {
        private const val JOURNALPOST_ID = "222"
        private const val FAGSAK_ID = 1L
        private const val MOTTAK_KANAL = "NAV_NO"
    }
}
