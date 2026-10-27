package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.DokarkivClient
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.IntegrasjonException
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.RestMinimalFagsak
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Properties

class OppdaterOgFerdigstillJournalpostTaskTest {
    private val journalpostClient = mockk<JournalpostClient>()
    private val dokarkivClient = mockk<DokarkivClient>()
    private val taskService = mockk<TaskService>()
    private val baSakClient = mockk<BaSakClient>()
    private val ksSakClient = mockk<KsSakClient>()

    private val oppdaterOgFerdigstillJournalpostTask =
        OppdaterOgFerdigstillJournalpostTask(
            journalpostClient = journalpostClient,
            dokarkivClient = dokarkivClient,
            taskService = taskService,
            baSakClient = baSakClient,
            ksSakClient = ksSakClient,
        )

    private val journalpostId = "1"
    private val fagsakId = 2L

    private val lagredeTasks = mutableListOf<Task>()

    @BeforeEach
    fun setUp() {
        justRun { dokarkivClient.oppdaterJournalpostSak(any(), any(), any()) }
        justRun { dokarkivClient.ferdigstillJournalpost(any()) }
        every { taskService.save(capture(lagredeTasks)) } answers { firstArg() }
    }

    @Test
    fun `skal låse opp låst fagsak i ba-sak før journalposten oppdateres og ferdigstilles`() {
        // Arrange
        every { journalpostClient.hentJournalpost(journalpostId) } returns lagJournalpost(Tema.BAR)
        every { baSakClient.hentMinimalRestFagsak(fagsakId) } returns lagFagsak(FagsakStatus.LÅST)
        every { baSakClient.låsOppFagsak(fagsakId, any()) } returns lagFagsak(FagsakStatus.AVSLUTTET)

        // Act
        oppdaterOgFerdigstillJournalpostTask.doTask(lagTask())

        // Assert
        verifyOrder {
            baSakClient.låsOppFagsak(fagsakId, any())
            dokarkivClient.oppdaterJournalpostSak(any(), fagsakId.toString(), Tema.BAR)
            dokarkivClient.ferdigstillJournalpost(journalpostId)
        }
        assertThat(lagredeTasks.single().type).isEqualTo(OpprettSøknadBehandlingISakTask.TASK_STEP_TYPE)
    }

    @Test
    fun `skal låse opp låst fagsak i ks-sak før journalposten oppdateres og ferdigstilles`() {
        // Arrange
        every { journalpostClient.hentJournalpost(journalpostId) } returns lagJournalpost(Tema.KON)
        every { ksSakClient.hentMinimalRestFagsak(fagsakId) } returns lagFagsak(FagsakStatus.LÅST)
        every { ksSakClient.låsOppFagsak(fagsakId, any()) } returns lagFagsak(FagsakStatus.AVSLUTTET)

        // Act
        oppdaterOgFerdigstillJournalpostTask.doTask(lagTask())

        // Assert
        verifyOrder {
            ksSakClient.låsOppFagsak(fagsakId, any())
            dokarkivClient.oppdaterJournalpostSak(any(), fagsakId.toString(), Tema.KON)
            dokarkivClient.ferdigstillJournalpost(journalpostId)
        }
        assertThat(lagredeTasks.single().type).isEqualTo(OpprettSøknadBehandlingISakTask.TASK_STEP_TYPE)
    }

    @Test
    fun `skal oppdatere og ferdigstille journalpost uten opplåsing når fagsaken ikke er låst`() {
        // Arrange
        every { journalpostClient.hentJournalpost(journalpostId) } returns lagJournalpost(Tema.BAR)
        every { baSakClient.hentMinimalRestFagsak(fagsakId) } returns lagFagsak(FagsakStatus.AVSLUTTET)

        // Act
        oppdaterOgFerdigstillJournalpostTask.doTask(lagTask())

        // Assert
        verify(exactly = 0) { baSakClient.låsOppFagsak(any(), any()) }
        verifyOrder {
            dokarkivClient.oppdaterJournalpostSak(any(), fagsakId.toString(), Tema.BAR)
            dokarkivClient.ferdigstillJournalpost(journalpostId)
        }
        assertThat(lagredeTasks.single().type).isEqualTo(OpprettSøknadBehandlingISakTask.TASK_STEP_TYPE)
    }

    @Test
    fun `skal feile uten å ferdigstille journalposten når opplåsing av fagsak feiler`() {
        // Arrange
        every { journalpostClient.hentJournalpost(journalpostId) } returns lagJournalpost(Tema.BAR)
        every { baSakClient.hentMinimalRestFagsak(fagsakId) } returns lagFagsak(FagsakStatus.LÅST)
        every { baSakClient.låsOppFagsak(fagsakId, any()) } throws IntegrasjonException("Feil ved opplåsing av fagsak")

        // Act & Assert
        assertThrows<IntegrasjonException> { oppdaterOgFerdigstillJournalpostTask.doTask(lagTask()) }
        verify(exactly = 0) { dokarkivClient.oppdaterJournalpostSak(any(), any(), any()) }
        verify(exactly = 0) { dokarkivClient.ferdigstillJournalpost(any()) }
        assertThat(lagredeTasks).isEmpty()
    }

    private fun lagTask() =
        Task(
            type = OppdaterOgFerdigstillJournalpostTask.TASK_STEP_TYPE,
            payload = journalpostId,
            properties =
                Properties().apply {
                    this["fagsakId"] = fagsakId.toString()
                    this["sakssystemMarkering"] = ""
                },
        )

    private fun lagJournalpost(tema: Tema) =
        Journalpost(
            journalpostId = journalpostId,
            journalposttype = Journalposttype.I,
            journalstatus = Journalstatus.MOTTATT,
            tema = tema.name,
            bruker = Bruker("12345678910", BrukerIdType.FNR),
        )

    private fun lagFagsak(status: FagsakStatus) = RestMinimalFagsak(id = fagsakId, behandlinger = emptyList(), status = status)
}
