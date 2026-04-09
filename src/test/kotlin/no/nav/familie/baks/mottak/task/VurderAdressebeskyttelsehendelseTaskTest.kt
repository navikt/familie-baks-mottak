package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleService
import no.nav.familie.baks.mottak.integrasjoner.Adressebeskyttelse
import no.nav.familie.baks.mottak.integrasjoner.Adressebeskyttelsesgradering
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClientService
import no.nav.familie.baks.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.baks.mottak.integrasjoner.PdlMetadata
import no.nav.familie.baks.mottak.integrasjoner.PdlPersonData
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakSkjermetBarn
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.prosessering.domene.Task
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VurderAdressebeskyttelsehendelseTaskTest {
    private val mockPdlClient: PdlClientService = mockk()
    private val mockBaSakClient: BaSakClient = mockk()
    private val mockOppgaveClient: OppgaveClientService = mockk()
    private val featureToggleService: FeatureToggleService = mockk()

    private val vurderAdressebeskyttelseHendelseTask =
        VurderAdressebeskyttelsehendelseTask(
            baSakClient = mockBaSakClient,
            pdlClientService = mockPdlClient,
            oppgaveClient = mockOppgaveClient,
            featureToggleService = featureToggleService,
        )

    private val aktørId = "1234567890123"
    private val personIdent = "12345678901"
    private val task =
        Task(
            type = VurderAdressebeskyttelsehendelseTask.TASK_STEP_TYPE,
            payload = aktørId,
        )

    @BeforeEach
    fun setUp() {
        every { mockPdlClient.hentPersonident(aktørId, Tema.BAR) } returns personIdent
    }

    @Test
    fun `skal opprette oppgave når strengt fortrolig adressebeskyttelse er opphørt og løpende fagsak finnes`() {
        // Arrange
        every {
            mockPdlClient.hentPerson(personIdent, "hentperson-med-adressebeskyttelse", Tema.BAR, historikk = true)
        } returns
            PdlPersonData(
                adressebeskyttelse =
                    listOf(
                        Adressebeskyttelse(
                            gradering = Adressebeskyttelsesgradering.STRENGT_FORTROLIG,
                            metadata = PdlMetadata(historisk = true),
                        ),
                    ),
            )

        every { mockBaSakClient.hentFagsakForSkjermetBarn(personIdent) } returns
            listOf(RestFagsakSkjermetBarn(id = 1L, status = FagsakStatus.LØPENDE))

        val oppgaveSlot = slot<OppgaveVurderLivshendelseDto>()
        every { mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveSlot)) } returns OppgaveResponse(1L)

        // Act
        vurderAdressebeskyttelseHendelseTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockOppgaveClient.opprettVurderLivshendelseOppgave(any()) }
        assertThat(oppgaveSlot.captured.aktørId).isEqualTo(aktørId)
        assertThat(oppgaveSlot.captured.saksId).isEqualTo("1")
        assertThat(oppgaveSlot.captured.tema).isEqualTo(Tema.BAR)
        assertThat(oppgaveSlot.captured.enhetsId).isEqualTo("2103")
        assertThat(oppgaveSlot.captured.behandlingstema).isEqualTo(Behandlingstema.Barnetrygd.value)
    }

    @Test
    fun `skal opprette oppgave når fortrolig adressebeskyttelse er opphørt og løpende fagsak finnes`() {
        // Arrange
        every {
            mockPdlClient.hentPerson(personIdent, "hentperson-med-adressebeskyttelse", Tema.BAR, historikk = true)
        } returns
            PdlPersonData(
                adressebeskyttelse =
                    listOf(
                        Adressebeskyttelse(
                            gradering = Adressebeskyttelsesgradering.FORTROLIG,
                            metadata = PdlMetadata(historisk = true),
                        ),
                    ),
            )

        every { mockBaSakClient.hentFagsakForSkjermetBarn(personIdent) } returns
            listOf(RestFagsakSkjermetBarn(id = 1L, status = FagsakStatus.LØPENDE))

        val oppgaveSlot = slot<OppgaveVurderLivshendelseDto>()
        every { mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveSlot)) } returns OppgaveResponse(1L)

        // Act
        vurderAdressebeskyttelseHendelseTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockOppgaveClient.opprettVurderLivshendelseOppgave(any()) }
        assertThat(oppgaveSlot.captured.aktørId).isEqualTo(aktørId)
        assertThat(oppgaveSlot.captured.saksId).isEqualTo("1")
        assertThat(oppgaveSlot.captured.tema).isEqualTo(Tema.BAR)
        assertThat(oppgaveSlot.captured.enhetsId).isEqualTo("2103")
        assertThat(oppgaveSlot.captured.behandlingstema).isEqualTo(Behandlingstema.Barnetrygd.value)
    }

    @Test
    fun `skal ikke opprette oppgave om personen fortsatt har adressebeskyttelse`() {
        // Arrange
        every {
            mockPdlClient.hentPerson(personIdent, "hentperson-med-adressebeskyttelse", Tema.BAR, historikk = true)
        } returns
            PdlPersonData(
                adressebeskyttelse =
                    listOf(
                        Adressebeskyttelse(
                            gradering = Adressebeskyttelsesgradering.STRENGT_FORTROLIG,
                            metadata = PdlMetadata(historisk = false),
                        ),
                    ),
            )

        // Act
        vurderAdressebeskyttelseHendelseTask.doTask(task)

        // Assert
        verify(exactly = 0) { mockBaSakClient.hentFagsakForSkjermetBarn(any()) }
        verify(exactly = 0) { mockOppgaveClient.opprettVurderLivshendelseOppgave(any()) }
    }

    @Test
    fun `skal ikke opprette oppgave om personen aldri hadde adressebeskyttelse`() {
        // Arrange
        every {
            mockPdlClient.hentPerson(personIdent, "hentperson-med-adressebeskyttelse", Tema.BAR, historikk = true)
        } returns PdlPersonData(adressebeskyttelse = emptyList())

        // Act
        vurderAdressebeskyttelseHendelseTask.doTask(task)

        // Assert
        verify(exactly = 0) { mockBaSakClient.hentFagsakForSkjermetBarn(any()) }
        verify(exactly = 0) { mockOppgaveClient.opprettVurderLivshendelseOppgave(any()) }
    }

    @Test
    fun `skal ikke opprette oppgave når fagsak er avsluttet`() {
        // Arrange
        every {
            mockPdlClient.hentPerson(personIdent, "hentperson-med-adressebeskyttelse", Tema.BAR, historikk = true)
        } returns
            PdlPersonData(
                adressebeskyttelse =
                    listOf(
                        Adressebeskyttelse(
                            gradering = Adressebeskyttelsesgradering.STRENGT_FORTROLIG,
                            metadata = PdlMetadata(historisk = true),
                        ),
                    ),
            )

        every { mockBaSakClient.hentFagsakForSkjermetBarn(personIdent) } returns
            listOf(RestFagsakSkjermetBarn(id = 1L, status = FagsakStatus.AVSLUTTET))

        // Act
        vurderAdressebeskyttelseHendelseTask.doTask(task)

        // Assert
        verify(exactly = 0) { mockOppgaveClient.opprettVurderLivshendelseOppgave(any()) }
    }
}
