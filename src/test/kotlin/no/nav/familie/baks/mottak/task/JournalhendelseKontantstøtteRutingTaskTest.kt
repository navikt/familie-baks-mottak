package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.domene.personopplysning.Person
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.IdentInformasjon
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.PdlNotFoundException
import no.nav.familie.baks.mottak.integrasjoner.RestMinimalFagsak
import no.nav.familie.baks.mottak.journalføring.AutomatiskJournalføringKontantstøtteService
import no.nav.familie.baks.mottak.journalføring.JournalpostBrukerService
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.kontrakter.felles.oppgave.IdentGruppe
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.kontrakter.felles.personopplysning.ForelderBarnRelasjon
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.Properties
import kotlin.test.assertEquals

class JournalhendelseKontantstøtteRutingTaskTest {
    private val pdlClient: PdlClient = mockk()
    private val taskService: TaskService = mockk()
    private val ksSakClient: KsSakClient = mockk()
    private val journalpostClient: JournalpostClient = mockk()
    private val unleashService: UnleashNextMedContextService = mockk()
    private val automatiskJournalføringKontantstøtteService: AutomatiskJournalføringKontantstøtteService = mockk()
    private val journalpostBrukerService: JournalpostBrukerService = mockk()
    private val journalhendelseKontantstøtteRutingTask: JournalhendelseKontantstøtteRutingTask =
        JournalhendelseKontantstøtteRutingTask(
            ksSakClient = ksSakClient,
            taskService = taskService,
            journalpostClient = journalpostClient,
            automatiskJournalføringKontantstøtteService = automatiskJournalføringKontantstøtteService,
            journalpostBrukerService = journalpostBrukerService,
        )

    val søkerFnr = "12345678910"
    val barn1Fnr = "11223344556"
    val barn2Fnr = "11223344557"

    @Test
    fun `doTask - skal opprette OpprettJournalføringOppgaveTask med tom sakssystem-markering når et eller flere av barna har sak i Infotrygd men ingen løpende`() {
        // Arrange
        val taskSlot = slot<Task>()
        setupPDLMocks()
        setupKsSakClientMocks()
        every { journalpostBrukerService.tilPersonIdent(any(), any()) } returns "TEST"

        every { journalpostClient.hentJournalpost("1") } returns
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.JOURNALFOERT,
                bruker = Bruker("testId", type = BrukerIdType.AKTOERID),
            )

        every { taskService.save(capture(taskSlot)) } returns mockk()

        every {
            automatiskJournalføringKontantstøtteService.skalAutomatiskJournalføres(any())
        } returns false

        // Act
        journalhendelseKontantstøtteRutingTask.doTask(
            Task(
                type = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
                payload = "NAV_NO",
                properties =
                    Properties().apply {
                        this["personIdent"] = søkerFnr
                        this["journalpostId"] = "1"
                        this["fagsakId"] = "123"
                        this["tema"] = Tema.KON.name
                    },
            ),
        )

        // Assert
        assertEquals("", taskSlot.captured.payload)
    }

    @Test
    fun `doTask - skal opprette OpprettJournalføringOppgaveTask med tom sakssystem-markering når ingen av barna har sak i Infotrygd`() {
        // Arrange
        val taskSlot = slot<Task>()
        setupPDLMocks()
        setupKsSakClientMocks()
        every { journalpostBrukerService.tilPersonIdent(any(), any()) } returns "TEST"

        every { taskService.save(capture(taskSlot)) } returns mockk()

        every { journalpostClient.hentJournalpost("1") } returns
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.JOURNALFOERT,
                bruker = Bruker("testId", type = BrukerIdType.AKTOERID),
            )

        every {
            automatiskJournalføringKontantstøtteService.skalAutomatiskJournalføres(any())
        } returns false

        // Act
        journalhendelseKontantstøtteRutingTask.doTask(
            Task(
                type = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
                payload = "NAV_NO",
                properties =
                    Properties().apply {
                        this["personIdent"] = søkerFnr
                        this["journalpostId"] = "1"
                        this["fagsakId"] = "123"
                        this["tema"] = Tema.KON.name
                    },
            ),
        )

        // Assert
        assertEquals("", taskSlot.captured.payload)
    }

    @Test
    fun `doTask - skal opprette journalføring-oppgave dersom bruker er null på journalpost`() {
        // Arrange
        val journalpostId = "1"
        val taskSlot = slot<Task>()

        every { journalpostClient.hentJournalpost(journalpostId) } returns
            Journalpost(
                journalpostId = journalpostId,
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker = null,
            )

        every { taskService.save(capture(taskSlot)) } returns mockk()

        // Act
        journalhendelseKontantstøtteRutingTask.doTask(
            Task(
                type = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
                payload = "SKAN_IM",
                properties =
                    Properties().apply {
                        this["journalpostId"] = journalpostId
                        this["fagsakId"] = "123"
                        this["tema"] = Tema.KON.name
                    },
            ),
        )

        // Assert
        val opprettetTask = taskSlot.captured

        verify(exactly = 1) { taskService.save(any()) }
        assertEquals(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE, opprettetTask.type)
        assertEquals("Ingen bruker er satt på journalpost. Kan ikke utlede om bruker har sak i Infotrygd eller KS-sak.", opprettetTask.payload)
    }

    @Test
    fun `doTask - skal opprette journalføring-oppgave dersom vi ikke finner aktiv personIdent på aktørId`() {
        // Arrange
        val journalpostId = "1"
        val taskSlot = slot<Task>()

        every { journalpostClient.hentJournalpost(journalpostId) } returns
            Journalpost(
                journalpostId = journalpostId,
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
            )

        every { taskService.save(capture(taskSlot)) } returns mockk()

        every { journalpostBrukerService.tilPersonIdent(any(), any()) } throws
            PdlNotFoundException(
                msg = "Fant ikke aktive identer på person",
                uri = URI("/"),
                ident = "1234",
            )

        // Act
        journalhendelseKontantstøtteRutingTask.doTask(
            Task(
                type = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
                payload = "SKAN_IM",
                properties =
                    Properties().apply {
                        this["journalpostId"] = journalpostId
                        this["fagsakId"] = "123"
                        this["tema"] = Tema.KON.name
                    },
            ),
        )

        // Assert
        val opprettetTask = taskSlot.captured

        verify(exactly = 1) { taskService.save(any()) }
        assertThat(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE).isEqualTo(opprettetTask.type)
        assertThat("Fant ingen aktiv personIdent for denne journalpost brukeren.").isEqualTo(opprettetTask.payload)
    }

    private fun setupPDLMocks() {
        every { pdlClient.hentPersonMedRelasjoner(any(), any()) } returns
            Person(
                navn = "Ola Norman",
                forelderBarnRelasjoner =
                    setOf(
                        ForelderBarnRelasjon(barn1Fnr, FORELDERBARNRELASJONROLLE.BARN),
                        ForelderBarnRelasjon(barn2Fnr, FORELDERBARNRELASJONROLLE.BARN),
                    ),
            )
        every { pdlClient.hentIdenter(søkerFnr, any()) } returns
            listOf(
                IdentInformasjon(
                    ident = søkerFnr,
                    gruppe = IdentGruppe.FOLKEREGISTERIDENT.name,
                    historisk = false,
                ),
            )
        every { pdlClient.hentIdenter(barn1Fnr, any()) } returns
            listOf(
                IdentInformasjon(
                    ident = barn1Fnr,
                    gruppe = IdentGruppe.FOLKEREGISTERIDENT.name,
                    historisk = false,
                ),
            )
        every { pdlClient.hentIdenter(barn2Fnr, any()) } returns
            listOf(
                IdentInformasjon(
                    ident = barn2Fnr,
                    gruppe = IdentGruppe.FOLKEREGISTERIDENT.name,
                    historisk = false,
                ),
            )
    }

    private fun setupKsSakClientMocks() {
        every { ksSakClient.hentFagsaknummerPåPersonident("TEST") } returns 1L

        every { ksSakClient.hentMinimalRestFagsak(1L) } returns RestMinimalFagsak(id = 0, emptyList(), FagsakStatus.OPPRETTET)
    }
}
