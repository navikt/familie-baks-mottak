package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleService
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdBarnetrygdClient
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.baks.mottak.integrasjoner.PdlNotFoundException
import no.nav.familie.baks.mottak.journalføring.AutomatiskJournalføringBarnetrygdService
import no.nav.familie.baks.mottak.journalføring.JournalpostBrukerService
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.Properties
import kotlin.test.assertEquals

class JournalhendelseBarnetrygdRutingTaskTest {
    private val pdlClient: PdlClientService = mockk()
    private val baSakClient: BaSakClient = mockk()
    private val infotrygdBarnetrygdClient: InfotrygdBarnetrygdClient = mockk()
    private val taskService: TaskService = mockk()
    private val journalpostClient: JournalpostClient = mockk()
    private val featureToggleService: FeatureToggleService = mockk()
    private val automatiskJournalføringBarnetrygdService: AutomatiskJournalføringBarnetrygdService = mockk()
    private val journalpostBrukerService: JournalpostBrukerService = mockk()

    private val journalhendelseBarnetrygdRutingTask: JournalhendelseBarnetrygdRutingTask =
        JournalhendelseBarnetrygdRutingTask(
            pdlClientService = pdlClient,
            baSakClient = baSakClient,
            infotrygdBarnetrygdClient = infotrygdBarnetrygdClient,
            taskService = taskService,
            journalpostClient = journalpostClient,
            featureToggleService = featureToggleService,
            automatiskJournalføringBarnetrygdService = automatiskJournalføringBarnetrygdService,
            journalpostBrukerService = journalpostBrukerService,
        )

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
        journalhendelseBarnetrygdRutingTask.doTask(
            Task(
                type = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
                payload = "SKAN_IM",
                properties =
                    Properties().apply {
                        this["journalpostId"] = journalpostId
                        this["fagsakId"] = "123"
                        this["tema"] = Tema.BAR.name
                    },
            ),
        )

        // Assert
        val opprettetTask = taskSlot.captured

        verify(exactly = 1) { taskService.save(any()) }
        assertEquals(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE, opprettetTask.type)
        assertEquals("Ingen bruker er satt på journalpost. Kan ikke utlede om bruker har sak i Infotrygd eller BA-sak.", opprettetTask.payload)
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

        every { journalpostBrukerService.tilPersonIdent(any(), any()) } throws
            PdlNotFoundException(
                msg = "Fant ikke aktive identer på person",
                uri = URI("/"),
                ident = "1234",
            )

        every { taskService.save(capture(taskSlot)) } returns mockk()

        // Act
        journalhendelseBarnetrygdRutingTask.doTask(
            Task(
                type = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
                payload = "SKAN_IM",
                properties =
                    Properties().apply {
                        this["journalpostId"] = journalpostId
                        this["fagsakId"] = "123"
                        this["tema"] = Tema.BAR.name
                    },
            ),
        )

        // Assert
        val opprettetTask = taskSlot.captured

        verify(exactly = 1) { taskService.save(any()) }
        assertThat(OpprettJournalføringOppgaveTask.TASK_STEP_TYPE).isEqualTo(opprettetTask.type)
        assertThat("Fant ingen aktiv personIdent for denne journalpost brukeren.").isEqualTo(opprettetTask.payload)
    }
}
