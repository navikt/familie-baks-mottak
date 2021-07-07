package no.nav.familie.ba.mottak.task

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.ba.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.ba.mottak.integrasjoner.FagsakStatus.LØPENDE
import no.nav.familie.ba.mottak.task.VurderLivshendelseType.DØDSFALL
import no.nav.familie.ba.mottak.task.VurderLivshendelseType.UTFLYTTING
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
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


    private val vurderLivshendelseTask =
        VurderLivshendelseTask(mockOppgaveClient, mockTaskRepository, mockPdlClient, mockSakClient, mockAktørClient)


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


        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any()) } returns emptyList()

        every { mockOppgaveClient.opprettVurderLivshendelseOppgave(any()) } returns OppgaveResponse(42)


    }

    @Test
    fun `Ignorer livshendelser på person som ikke har barn`() {
        every {
            mockPdlClient.hentPerson(
                PERSONIDENT_BARN,
                any()
            )
        } returns PdlPersonData(
            forelderBarnRelasjon = listOf(
                PdlForeldreBarnRelasjon(
                    minRolleForPerson = Familierelasjonsrolle.BARN,
                    relatertPersonsIdent = PERSONIDENT_MOR,
                    relatertPersonsRolle = Familierelasjonsrolle.MOR
                ),
                PdlForeldreBarnRelasjon(
                    minRolleForPerson = Familierelasjonsrolle.BARN,
                    relatertPersonsIdent = PERSONIDENT_FAR,
                    relatertPersonsRolle = Familierelasjonsrolle.FAR
                )
            ),
            dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())),
            fødsel = listOf(Fødsel(LocalDate.of(1980, 8, 3)))
        )

        listOf(UTFLYTTING, DØDSFALL).forEach {
            vurderLivshendelseTask.doTask(
                    Task.nyTask(
                            type = VurderLivshendelseTask.TASK_STEP_TYPE,
                            payload = objectMapper.writeValueAsString(
                                    VurderLivshendelseTaskDTO(
                                            PERSONIDENT_BARN,
                                            it
                                    )
                            )
                    )
            )
        }

        verify(exactly = 0) {
            mockTaskRepository.saveAndFlush(any())
            mockOppgaveClient.opprettVurderLivshendelseOppgave(any())
            mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_BARN, listOf())
            mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN))
            mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_FAR, listOf(PERSONIDENT_BARN))
        }

    }

    @Test
    fun `Ignorer livshendelser på person som har barn og som ikke har sak i ba-sak`() {

        every {
            mockPdlClient.hentPerson(
                PERSONIDENT_MOR,
                any()
            )
        } returns PdlPersonData(
            forelderBarnRelasjon = listOf(
                PdlForeldreBarnRelasjon(
                    minRolleForPerson = Familierelasjonsrolle.MOR,
                    relatertPersonsIdent = PERSONIDENT_BARN,
                    relatertPersonsRolle = Familierelasjonsrolle.BARN
                )
            ),
            dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now()))
        )

        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns emptyList()

        listOf(UTFLYTTING, DØDSFALL).forEach {
            vurderLivshendelseTask.doTask(
                    Task.nyTask(
                            type = VurderLivshendelseTask.TASK_STEP_TYPE,
                            payload = objectMapper.writeValueAsString(
                                    VurderLivshendelseTaskDTO(
                                            PERSONIDENT_MOR,
                                            it
                                    )
                            )
                    )
            )
        }

        verify(exactly = 0) {
            mockTaskRepository.saveAndFlush(any())
            mockOppgaveClient.opprettVurderLivshendelseOppgave(any())
        }

        verify(exactly = 2) {
            mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList())
        }
    }

    @Test
    fun `Livshendelser på person som har sak i ba-sak`() {

        every {
            mockPdlClient.hentPerson(
                any(),
                any()
            )
        } returns PdlPersonData(
            forelderBarnRelasjon = listOf(
                PdlForeldreBarnRelasjon(
                    minRolleForPerson = Familierelasjonsrolle.MOR,
                    relatertPersonsIdent = PERSONIDENT_BARN,
                    relatertPersonsRolle = Familierelasjonsrolle.BARN
                )
            ),
            dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now()))
        )

        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns
                listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE))

        every { mockSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivOrdinær()

        listOf(DØDSFALL, UTFLYTTING).forEach {
            vurderLivshendelseTask.doTask(
                    Task.nyTask(
                            type = VurderLivshendelseTask.TASK_STEP_TYPE,
                            payload = objectMapper.writeValueAsString(
                                    VurderLivshendelseTaskDTO(
                                            PERSONIDENT_MOR,
                                            it
                                    )
                            )
                    )
            )
        }

        val oppgaveDto = mutableListOf<OppgaveVurderLivshendelseDto>()
        verify(exactly = 2) {
            mockTaskRepository.saveAndFlush(any())
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto))
        }

        assertThat(oppgaveDto[0].beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_DØDSFALL)
        assertThat(oppgaveDto[1].beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_UTFLYTTING.format("bruker"))

        assertThat(oppgaveDto).allMatch { it.aktørId.contains(PERSONIDENT_MOR) }
        assertThat(oppgaveDto).allMatch { it.saksId == "$SAKS_ID"  }
        assertThat(oppgaveDto).allMatch { it.enhetsId == ENHET_ID }
        assertThat(oppgaveDto).allMatch { it.behandlingstema == Behandlingstema.OrdinærBarnetrygd.value }
        assertThat(oppgaveDto).allMatch { it.behandlesAvApplikasjon == BehandlesAvApplikasjon.BA_SAK.applikasjon }
    }

    @Test
    fun `Livshendelser på BARN som har sak i ba-sak`() {

        every {
            mockPdlClient.hentPerson(
                PERSONIDENT_BARN,
                any()
            )
        } returns PdlPersonData(
            forelderBarnRelasjon = listOf(
                PdlForeldreBarnRelasjon(
                    minRolleForPerson = Familierelasjonsrolle.BARN,
                    relatertPersonsIdent = PERSONIDENT_MOR,
                    relatertPersonsRolle = Familierelasjonsrolle.MOR
                ),
                PdlForeldreBarnRelasjon(
                    minRolleForPerson = Familierelasjonsrolle.BARN,
                    relatertPersonsIdent = PERSONIDENT_FAR,
                    relatertPersonsRolle = Familierelasjonsrolle.FAR
                )
            ),
            dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())),
            fødsel = listOf(Fødsel(LocalDate.now().minusYears(12)))
        )


        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns
                listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE))

        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_FAR, emptyList()) } returns emptyList()

        every { mockSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()

        listOf(DØDSFALL, UTFLYTTING).forEach {
            vurderLivshendelseTask.doTask(
                    Task.nyTask(
                            type = VurderLivshendelseTask.TASK_STEP_TYPE,
                            payload = objectMapper.writeValueAsString(
                                    VurderLivshendelseTaskDTO(
                                            PERSONIDENT_BARN,
                                            it
                                    )
                            )
                    )
            )
        }

        val oppgaveDto = mutableListOf<OppgaveVurderLivshendelseDto>()
        verify(exactly = 2) {
            mockTaskRepository.saveAndFlush(any())
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto))
        }

        assertThat(oppgaveDto[0].beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_DØDSFALL)
        assertThat(oppgaveDto[1].beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_UTFLYTTING.format("barn $PERSONIDENT_BARN"))

        assertThat(oppgaveDto).allMatch { it.aktørId.contains(PERSONIDENT_MOR) }
        assertThat(oppgaveDto).allMatch { it.saksId == "$SAKS_ID" }
        assertThat(oppgaveDto).allMatch { it.enhetsId == ENHET_ID }
        assertThat(oppgaveDto).allMatch { it.behandlingstema == Behandlingstema.UtvidetBarnetrygd.value }
        assertThat(oppgaveDto).allMatch { it.behandlesAvApplikasjon == BehandlesAvApplikasjon.BA_SAK.applikasjon }
    }


    private fun lagAktivOrdinær() = RestFagsak(
            SAKS_ID,
            listOf(
            RestUtvidetBehandling(
                true,
                RestArbeidsfordelingPåBehandling(ENHET_ID),
                321,
                BehandlingKategori.NASJONAL,
                LocalDateTime.now(),
                "RESULTAT",
                "STEG",
                "TYPE",
                BehandlingUnderkategori.ORDINÆR
            )
        )
    )

    private fun lagAktivUtvidet() = RestFagsak(
        SAKS_ID.toLong(),
        listOf(
            RestUtvidetBehandling(
                true,
                RestArbeidsfordelingPåBehandling(ENHET_ID),
                321,
                BehandlingKategori.NASJONAL,
                LocalDateTime.now(),
                "RESULTAT",
                "STEG",
                "TYPE",
                BehandlingUnderkategori.UTVIDET
            )
        )
    )


    companion object {

        private val PERSONIDENT_BARN = "12345654321"
        private val PERSONIDENT_MOR = "12345678901"
        private val PERSONIDENT_FAR = "12345678888"
        private val SAKS_ID = 123L
        private val ENHET_ID = "3049"
    }
}
