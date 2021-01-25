package no.nav.familie.ba.mottak.task

import io.mockk.*
import no.nav.familie.ba.mottak.config.FeatureToggleService
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstema
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VurderLivshendelseTaskTest {

    private val mockOppgaveClient: OppgaveClient = mockk()
    private val mockSakClient: SakClient = mockk()
    private val mockAktørClient: AktørClient = mockk()
    private val mockTaskRepository: TaskRepository = mockk(relaxed = true)
    private val mockPdlClient: PdlClient = mockk(relaxed = true)
    private val mockFeatureToggleService: FeatureToggleService = mockk()


    private val vurderLivshendelseTask =
            VurderLivshendelseTask(mockOppgaveClient, mockTaskRepository, mockPdlClient, mockSakClient, mockFeatureToggleService, mockAktørClient)


    @BeforeEach
    internal fun setUp() {
        clearAllMocks()

        every {
            mockOppgaveClient.finnOppgaver(any(), any())
        } returns listOf()

        every {
            mockTaskRepository.saveAndFlush(any<Task>())
        } returns null

        every {
            mockAktørClient.hentAktørId(PERSONIDENT_MOR)
        } returns PERSONIDENT_MOR + "00"

        every {
            mockAktørClient.hentAktørId(PERSONIDENT_FAR)
        } returns PERSONIDENT_FAR + "00"

        every {
            mockAktørClient.hentAktørId(PERSONIDENT_BARN)
        } returns PERSONIDENT_BARN + "00"

        every {
            mockFeatureToggleService.isEnabled(any(), any())
        } returns true

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any()) } returns emptyList()

        every { mockOppgaveClient.opprettVurderLivshendelseOppgave(any()) } returns OppgaveResponse(42)


    }

    @Test
    fun `Ignorer dødsfallhendelser på person som ikke har barn`() {
        every {
            mockPdlClient.hentPerson(PERSONIDENT_BARN,
                                     any())
        } returns PdlPersonData(familierelasjoner = listOf(PdlFamilierelasjon(minRolleForPerson = Familierelasjonsrolle.BARN,
                                                                              relatertPersonsIdent = PERSONIDENT_MOR,
                                                                              relatertPersonsRolle = Familierelasjonsrolle.MOR),
                                                           PdlFamilierelasjon(minRolleForPerson = Familierelasjonsrolle.BARN,
                                                                              relatertPersonsIdent = PERSONIDENT_FAR,
                                                                              relatertPersonsRolle = Familierelasjonsrolle.FAR)),
                                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())),
        fødsel = listOf(Fødsel(LocalDate.of(1980,8,3))))

        vurderLivshendelseTask.doTask(Task.nyTask(type = VurderLivshendelseTask.TASK_STEP_TYPE,
                                                  payload = objectMapper.writeValueAsString(VurderLivshendelseTaskDTO(
                                                          PERSONIDENT_BARN,
                                                          VurderLivshendelseType.DØDSFALL))))

        verify(exactly = 0) {
            mockTaskRepository.saveAndFlush(any())
            mockOppgaveClient.opprettVurderLivshendelseOppgave(any())
            mockSakClient.hentPågåendeSakStatus(PERSONIDENT_BARN, listOf())
            mockSakClient.hentPågåendeSakStatus(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN))
            mockSakClient.hentPågåendeSakStatus(PERSONIDENT_FAR, listOf(PERSONIDENT_BARN))
        }

    }

    @Test
    fun `Ignorer dødsfallhendelser på person som har barn og som ikke har sak i ba-sak`() {

        every {
            mockPdlClient.hentPerson(PERSONIDENT_MOR,
                                     any())
        } returns PdlPersonData(familierelasjoner = listOf(PdlFamilierelasjon(minRolleForPerson = Familierelasjonsrolle.MOR,
                                                                              relatertPersonsIdent = PERSONIDENT_BARN,
                                                                              relatertPersonsRolle = Familierelasjonsrolle.BARN)),
                                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())))

        every { mockSakClient.hentPågåendeSakStatus(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns RestPågåendeSakResponse()

        vurderLivshendelseTask.doTask(Task.nyTask(type = VurderLivshendelseTask.TASK_STEP_TYPE,
                                                  payload = objectMapper.writeValueAsString(VurderLivshendelseTaskDTO(
                                                          PERSONIDENT_MOR,
                                                          VurderLivshendelseType.DØDSFALL))))
        verify(exactly = 0) {
            mockTaskRepository.saveAndFlush(any())
            mockOppgaveClient.opprettVurderLivshendelseOppgave(any())
        }

        verify(exactly = 1) {
            mockSakClient.hentPågåendeSakStatus(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN))
        }
    }

    @Test
    fun `Ignorer dødsfallhendelser på person som har barn og hvor annen part er den som har søknad i ba-sak`() {

        every {
            mockPdlClient.hentPerson(PERSONIDENT_MOR,
                                     any())
        } returns PdlPersonData(familierelasjoner = listOf(PdlFamilierelasjon(minRolleForPerson = Familierelasjonsrolle.MOR,
                                                                              relatertPersonsIdent = PERSONIDENT_BARN,
                                                                              relatertPersonsRolle = Familierelasjonsrolle.BARN)),
                                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())))

        every { mockSakClient.hentPågåendeSakStatus(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns RestPågåendeSakResponse(
                baSak = Sakspart.ANNEN
        )

        vurderLivshendelseTask.doTask(Task.nyTask(type = VurderLivshendelseTask.TASK_STEP_TYPE,
                                                  payload = objectMapper.writeValueAsString(VurderLivshendelseTaskDTO(
                                                          PERSONIDENT_MOR,
                                                          VurderLivshendelseType.DØDSFALL))))
        verify(exactly = 0) {
            mockTaskRepository.saveAndFlush(any())
            mockOppgaveClient.opprettVurderLivshendelseOppgave(any())
        }
        verify(exactly = 1) {
            mockSakClient.hentPågåendeSakStatus(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN))
        }
    }


    @Test
    fun `Dødsfallhendelse på SØKER som har sak i ba-sak`() {

        every {
            mockPdlClient.hentPerson(any(),
                                     any())
        } returns PdlPersonData(familierelasjoner = listOf(PdlFamilierelasjon(minRolleForPerson = Familierelasjonsrolle.MOR,
                                                                              relatertPersonsIdent = PERSONIDENT_BARN,
                                                                              relatertPersonsRolle = Familierelasjonsrolle.BARN)),
                                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())))

        every { mockSakClient.hentPågåendeSakStatus(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns RestPågåendeSakResponse(
                baSak = Sakspart.SØKER)
        every { mockSakClient.hentRestFagsak(PERSONIDENT_MOR) } returns lagAktivOrdinær()

        vurderLivshendelseTask.doTask(Task.nyTask(type = VurderLivshendelseTask.TASK_STEP_TYPE,
                                                  payload = objectMapper.writeValueAsString(VurderLivshendelseTaskDTO(
                                                          PERSONIDENT_MOR,
                                                          VurderLivshendelseType.DØDSFALL))))


        val oppgaveDtoSlot =  slot<OppgaveVurderLivshendelseDto>()
        verify(exactly = 1) {
            mockTaskRepository.saveAndFlush(any())
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDtoSlot))
        }

        assertThat(oppgaveDtoSlot.captured.aktørId).contains(PERSONIDENT_MOR)
        assertThat(oppgaveDtoSlot.captured.saksId).isEqualTo(SAKS_ID)
        assertThat(oppgaveDtoSlot.captured.beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_DØDSFALL)
        assertThat(oppgaveDtoSlot.captured.enhetsId).isEqualTo(ENHET_ID)
        assertThat(oppgaveDtoSlot.captured.behandlingstema).isEqualTo(Behandlingstema.OrdinærBarnetrygd.value)
    }


    @Test
    fun `Dødsfallhendelse på BARN som har sak i ba-sak`() {

        every {
            mockPdlClient.hentPerson(PERSONIDENT_BARN,
                                     any())
        } returns PdlPersonData(familierelasjoner = listOf(PdlFamilierelasjon(minRolleForPerson = Familierelasjonsrolle.BARN,
                                                                              relatertPersonsIdent = PERSONIDENT_MOR,
                                                                              relatertPersonsRolle = Familierelasjonsrolle.MOR),
                                                           PdlFamilierelasjon(minRolleForPerson = Familierelasjonsrolle.BARN,
                                                                              relatertPersonsIdent = PERSONIDENT_FAR,
                                                                              relatertPersonsRolle = Familierelasjonsrolle.FAR)),
                                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())),
                                fødsel = listOf(Fødsel(LocalDate.now().minusYears(12))))


        every { mockSakClient.hentPågåendeSakStatus(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns RestPågåendeSakResponse(
                baSak = Sakspart.SØKER)
        every { mockSakClient.hentPågåendeSakStatus(PERSONIDENT_FAR, listOf(PERSONIDENT_BARN)) } returns RestPågåendeSakResponse(
                baSak = Sakspart.ANNEN)
        every { mockSakClient.hentRestFagsak(PERSONIDENT_MOR) } returns lagAktivUtvidet()

        vurderLivshendelseTask.doTask(Task.nyTask(type = VurderLivshendelseTask.TASK_STEP_TYPE,
                                                  payload = objectMapper.writeValueAsString(VurderLivshendelseTaskDTO(
                                                          PERSONIDENT_BARN,
                                                          VurderLivshendelseType.DØDSFALL))))


        val oppgaveDtoSlot =  slot<OppgaveVurderLivshendelseDto>()
        verify(exactly = 1) {
            mockTaskRepository.saveAndFlush(any())
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDtoSlot))
        }

        assertThat(oppgaveDtoSlot.captured.aktørId).contains(PERSONIDENT_MOR)
        assertThat(oppgaveDtoSlot.captured.saksId).isEqualTo(SAKS_ID)
        assertThat(oppgaveDtoSlot.captured.beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_DØDSFALL)
        assertThat(oppgaveDtoSlot.captured.enhetsId).isEqualTo(ENHET_ID)
        assertThat(oppgaveDtoSlot.captured.behandlingstema).isEqualTo(Behandlingstema.UtvidetBarnetrygd.value)
    }


    private fun lagAktivOrdinær() = RestFagsak(SAKS_ID.toLong(),
                                               listOf(RestUtvidetBehandling(true,
                                                                            RestArbeidsfordelingPåBehandling(ENHET_ID),
                                                                            321,
                                                                            BehandlingKategori.NASJONAL,
                                                                            LocalDateTime.now(),
                                                                            BehandlingUnderkategori.ORDINÆR)))
    private fun lagAktivUtvidet() = RestFagsak(SAKS_ID.toLong(),
                                               listOf(RestUtvidetBehandling(true,
                                                                            RestArbeidsfordelingPåBehandling(ENHET_ID),
                                                                            321,
                                                                            BehandlingKategori.NASJONAL,
                                                                            LocalDateTime.now(),
                                                                            BehandlingUnderkategori.UTVIDET)))


    companion object {

        private val PERSONIDENT_BARN = "12345654321"
        private val PERSONIDENT_MOR = "12345678901"
        private val PERSONIDENT_FAR = "12345678888"
        private val SAKS_ID = "123"
        private val ENHET_ID = "3049"
    }
}
