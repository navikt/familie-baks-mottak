package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.domene.personopplysning.Person
import no.nav.familie.baks.mottak.integrasjoner.BarnDto
import no.nav.familie.baks.mottak.integrasjoner.Bruker
import no.nav.familie.baks.mottak.integrasjoner.BrukerIdType
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.Foedselsnummer
import no.nav.familie.baks.mottak.integrasjoner.IdentInformasjon
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdKontantstøtteClient
import no.nav.familie.baks.mottak.integrasjoner.InnsynResponse
import no.nav.familie.baks.mottak.integrasjoner.Journalpost
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.Journalposttype
import no.nav.familie.baks.mottak.integrasjoner.Journalstatus
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.RestMinimalFagsak
import no.nav.familie.baks.mottak.integrasjoner.StonadDto
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.oppgave.IdentGruppe
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.kontrakter.felles.personopplysning.ForelderBarnRelasjon
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.YearMonth
import java.util.Properties
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class JournalhendelseKontantstøtteRutingTaskTest {
    @MockK
    private lateinit var pdlClient: PdlClient

    @MockK
    private lateinit var infotrygdKontantstøtteClient: InfotrygdKontantstøtteClient

    @MockK
    private lateinit var taskService: TaskService

    @MockK
    private lateinit var ksSakClient: KsSakClient

    @MockK
    private lateinit var journalpostClient: JournalpostClient

    @MockK
    private lateinit var unleashService: UnleashNextMedContextService

    @InjectMockKs
    private lateinit var journalhendelseKontantstøtteRutingTask: JournalhendelseKontantstøtteRutingTask

    val søkerFnr = "12345678910"
    val barn1Fnr = "11223344556"
    val barn2Fnr = "11223344557"

    @Test
    fun `doTask - skal opprette OpprettJournalføringOppgaveTask med informasjon om at det finnes løpende sak i Infotrygd når et eller flere av barna har løpende sak i Infotrygd`() {
        val taskSlot = slot<Task>()
        setupPDLMocks()
        setupKsSakClientMocks()

        every { unleashService.isEnabled(FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_KONTANTSTØTTE_SØKNADER, false) } returns false
        every { infotrygdKontantstøtteClient.harKontantstøtteIInfotrygd(any()) } returns true
        every { journalpostClient.hentJournalpost("1") } returns
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.JOURNALFOERT,
                bruker = Bruker("testId", type = BrukerIdType.AKTOERID),
            )

        every { infotrygdKontantstøtteClient.hentPerioderMedKontantstotteIInfotrygdByBarn(any()) } returns
            InnsynResponse(
                data =
                    listOf(
                        StonadDto(
                            fnr = Foedselsnummer(søkerFnr),
                            YearMonth.now().minusMonths(5),
                            YearMonth.now().plusMonths(1),
                            belop = 1000,
                            listOf(
                                BarnDto(Foedselsnummer(barn1Fnr)),
                                BarnDto(Foedselsnummer(barn2Fnr)),
                            ),
                        ),
                    ),
            )
        every { taskService.save(capture(taskSlot)) } returns mockk()

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

        assertEquals("Et eller flere av barna har løpende sak i Infotrygd", taskSlot.captured.payload)
    }

    @Test
    fun `doTask - skal opprette OpprettJournalføringOppgaveTask med tom sakssystem-markering når et eller flere av barna har sak i Infotrygd men ingen løpende`() {
        val taskSlot = slot<Task>()
        setupPDLMocks()
        setupKsSakClientMocks()

        every { unleashService.isEnabled(FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_KONTANTSTØTTE_SØKNADER, false) } returns false
        every { infotrygdKontantstøtteClient.harKontantstøtteIInfotrygd(any()) } returns true
        every { journalpostClient.hentJournalpost("1") } returns
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.JOURNALFOERT,
                bruker = Bruker("testId", type = BrukerIdType.AKTOERID),
            )

        every { infotrygdKontantstøtteClient.hentPerioderMedKontantstotteIInfotrygdByBarn(any()) } returns
            InnsynResponse(
                data =
                    listOf(
                        StonadDto(
                            fnr = Foedselsnummer(søkerFnr),
                            YearMonth.now().minusMonths(8),
                            YearMonth.now().minusMonths(1),
                            belop = 1000,
                            listOf(
                                BarnDto(Foedselsnummer(barn1Fnr)),
                                BarnDto(Foedselsnummer(barn2Fnr)),
                            ),
                        ),
                    ),
            )
        every { taskService.save(capture(taskSlot)) } returns mockk()

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

        assertEquals("", taskSlot.captured.payload)
    }

    @Test
    fun `doTask - skal opprette OpprettJournalføringOppgaveTask med tom sakssystem-markering når ingen av barna har sak i Infotrygd`() {
        val taskSlot = slot<Task>()
        setupPDLMocks()
        setupKsSakClientMocks()

        every { unleashService.isEnabled(FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_KONTANTSTØTTE_SØKNADER, false) } returns false
        every { infotrygdKontantstøtteClient.harKontantstøtteIInfotrygd(any()) } returns false

        every { taskService.save(capture(taskSlot)) } returns mockk()

        every { journalpostClient.hentJournalpost("1") } returns
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.JOURNALFOERT,
                bruker = Bruker("testId", type = BrukerIdType.AKTOERID),
            )
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

        assertEquals("", taskSlot.captured.payload)
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

        every { pdlClient.hentPersonident(any(), any()) } returns "TEST"
    }

    private fun setupKsSakClientMocks() {
        every { ksSakClient.hentFagsaknummerPåPersonident("TEST") } returns 1L

        every { ksSakClient.hentMinimalRestFagsak(1L) } returns RestMinimalFagsak(id = 0, emptyList(), FagsakStatus.OPPRETTET)
    }
}
