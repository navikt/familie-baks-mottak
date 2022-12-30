package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import no.nav.familie.baks.mottak.domene.personopplysning.Person
import no.nav.familie.baks.mottak.integrasjoner.BarnDto
import no.nav.familie.baks.mottak.integrasjoner.Foedselsnummer
import no.nav.familie.baks.mottak.integrasjoner.IdentInformasjon
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdKontantstøtteClient
import no.nav.familie.baks.mottak.integrasjoner.InnsynResponse
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.StonadDto
import no.nav.familie.kontrakter.felles.oppgave.IdentGruppe
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.kontrakter.felles.personopplysning.ForelderBarnRelasjon
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.YearMonth
import java.util.*
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class JournalhendelseKontantstøtteRutingTaskTest {

    @MockK
    private lateinit var pdlClient: PdlClient

    @MockK
    private lateinit var infotrygdKontantstøtteClient: InfotrygdKontantstøtteClient

    @MockK
    private lateinit var taskService: TaskService

    @InjectMockKs
    private lateinit var journalhendelseKontantstøtteRutingTask: JournalhendelseKontantstøtteRutingTask

    val søkerFnr = "12345678910"
    val barn1Fnr = "11223344556"
    val barn2Fnr = "11223344557"

    @Test
    fun `doTask - skal opprette OpprettJournalføringOppgaveTask med informasjon om at det finnes løpende sak i Infotrygd når et eller flere av barna har løpende sak i Infotrygd`() {
        val taskSlot = slot<Task>()
        setupPDLMocks()
        every { infotrygdKontantstøtteClient.harKontantstøtteIInfotrygd(any()) } returns true
        every { infotrygdKontantstøtteClient.hentPerioderMedKontantstøtteIInfotrygd(any()) } returns InnsynResponse(
            data = listOf(
                StonadDto(
                    fnr = Foedselsnummer(søkerFnr),
                    YearMonth.now().minusMonths(5),
                    YearMonth.now().plusMonths(1),
                    belop = 1000,
                    listOf(
                        BarnDto(Foedselsnummer(barn1Fnr)),
                        BarnDto(Foedselsnummer(barn2Fnr))
                    )
                )
            )
        )
        every { taskService.save(capture(taskSlot)) } returns mockk()

        journalhendelseKontantstøtteRutingTask.doTask(
            Task(
                type = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
                payload = "NAV_NO",
                properties = Properties().apply { this["personIdent"] = søkerFnr }
            )
        )

        assertEquals("Et eller flere av barna har løpende sak i Infotrygd", taskSlot.captured.payload)
    }

    @Test
    fun `doTask - skal opprette OpprettJournalføringOppgaveTask med tom sakssystem-markering når et eller flere av barna har sak i Infotrygd men ingen løpende`() {
        val taskSlot = slot<Task>()
        setupPDLMocks()
        every { infotrygdKontantstøtteClient.harKontantstøtteIInfotrygd(any()) } returns true
        every { infotrygdKontantstøtteClient.hentPerioderMedKontantstøtteIInfotrygd(any()) } returns InnsynResponse(
            data = listOf(
                StonadDto(
                    fnr = Foedselsnummer(søkerFnr),
                    YearMonth.now().minusMonths(8),
                    YearMonth.now().minusMonths(1),
                    belop = 1000,
                    listOf(
                        BarnDto(Foedselsnummer(barn1Fnr)),
                        BarnDto(Foedselsnummer(barn2Fnr))
                    )
                )
            )
        )
        every { taskService.save(capture(taskSlot)) } returns mockk()

        journalhendelseKontantstøtteRutingTask.doTask(
            Task(
                type = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
                payload = "NAV_NO",
                properties = Properties().apply { this["personIdent"] = søkerFnr }
            )
        )

        assertEquals("", taskSlot.captured.payload)
    }

    @Test
    fun `doTask - skal opprette OpprettJournalføringOppgaveTask med tom sakssystem-markering når ingen av barna har sak i Infotrygd`() {
        val taskSlot = slot<Task>()
        setupPDLMocks()
        every { infotrygdKontantstøtteClient.harKontantstøtteIInfotrygd(any()) } returns false

        every { taskService.save(capture(taskSlot)) } returns mockk()

        journalhendelseKontantstøtteRutingTask.doTask(
            Task(
                type = JournalhendelseKontantstøtteRutingTask.TASK_STEP_TYPE,
                payload = "NAV_NO",
                properties = Properties().apply { this["personIdent"] = søkerFnr }
            )
        )

        assertEquals("", taskSlot.captured.payload)
    }

    private fun setupPDLMocks() {
        every { pdlClient.hentPersonMedRelasjoner(any()) } returns Person(
            navn = "Ola Norman",
            forelderBarnRelasjoner = setOf(
                ForelderBarnRelasjon(barn1Fnr, FORELDERBARNRELASJONROLLE.BARN),
                ForelderBarnRelasjon(barn2Fnr, FORELDERBARNRELASJONROLLE.BARN)
            )
        )
        every { pdlClient.hentIdenter(barn1Fnr) } returns listOf(
            IdentInformasjon(
                ident = barn1Fnr,
                gruppe = IdentGruppe.FOLKEREGISTERIDENT.name,
                historisk = false
            )
        )
        every { pdlClient.hentIdenter(barn2Fnr) } returns listOf(
            IdentInformasjon(
                ident = barn2Fnr,
                gruppe = IdentGruppe.FOLKEREGISTERIDENT.name,
                historisk = false
            )
        )
    }
}
