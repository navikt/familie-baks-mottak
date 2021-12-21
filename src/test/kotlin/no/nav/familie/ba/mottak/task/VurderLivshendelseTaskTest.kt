package no.nav.familie.ba.mottak.task

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.ba.mottak.integrasjoner.AktørClient
import no.nav.familie.ba.mottak.integrasjoner.BehandlesAvApplikasjon
import no.nav.familie.ba.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.ba.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.ba.mottak.integrasjoner.Dødsfall
import no.nav.familie.ba.mottak.integrasjoner.FagsakDeltagerRolle.BARN
import no.nav.familie.ba.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.ba.mottak.integrasjoner.FagsakStatus.LØPENDE
import no.nav.familie.ba.mottak.integrasjoner.Fødsel
import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import no.nav.familie.ba.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.ba.mottak.integrasjoner.PdlClient
import no.nav.familie.ba.mottak.integrasjoner.PdlForeldreBarnRelasjon
import no.nav.familie.ba.mottak.integrasjoner.PdlPersonData
import no.nav.familie.ba.mottak.integrasjoner.RestArbeidsfordelingPåBehandling
import no.nav.familie.ba.mottak.integrasjoner.RestFagsak
import no.nav.familie.ba.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.ba.mottak.integrasjoner.RestMinimalFagsak
import no.nav.familie.ba.mottak.integrasjoner.RestUtvidetBehandling
import no.nav.familie.ba.mottak.integrasjoner.RestVisningBehandling
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.ba.mottak.task.VurderLivshendelseType.DØDSFALL
import no.nav.familie.ba.mottak.task.VurderLivshendelseType.UTFLYTTING
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
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
    fun `Ignorer livshendelser på person som ikke har barn eller fagsaktilhørighet`() {
        every {
            mockPdlClient.hentPerson(
                    PERSONIDENT_BARN,
                    any()
            )
        } returns PdlPersonData(
                forelderBarnRelasjon = listOf(
                        PdlForeldreBarnRelasjon(
                                minRolleForPerson = FORELDERBARNRELASJONROLLE.BARN,
                                relatertPersonsIdent = PERSONIDENT_MOR,
                                relatertPersonsRolle = FORELDERBARNRELASJONROLLE.MOR
                        ),
                        PdlForeldreBarnRelasjon(
                                minRolleForPerson = FORELDERBARNRELASJONROLLE.BARN,
                                relatertPersonsIdent = PERSONIDENT_FAR,
                                relatertPersonsRolle = FORELDERBARNRELASJONROLLE.FAR
                        )
                ),
                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())),
        )
        every { mockSakClient.hentRestFagsakDeltagerListe(any(), listOf(PERSONIDENT_BARN)) } returns
                emptyList()

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
        }
        verify(exactly = 2) {
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
                                minRolleForPerson = FORELDERBARNRELASJONROLLE.MOR,
                                relatertPersonsIdent = PERSONIDENT_BARN,
                                relatertPersonsRolle = FORELDERBARNRELASJONROLLE.BARN
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
    fun `Skal opprette oppgave på forelderen barnet er registrert på i ba-sak`() {

        every {
            mockPdlClient.hentPerson(
                PERSONIDENT_BARN,
                any()
            )
        } returns PdlPersonData(
            forelderBarnRelasjon = listOf(
                PdlForeldreBarnRelasjon(
                    minRolleForPerson = FORELDERBARNRELASJONROLLE.BARN,
                    relatertPersonsIdent = PERSONIDENT_MOR,
                    relatertPersonsRolle = FORELDERBARNRELASJONROLLE.MOR
                ),
                PdlForeldreBarnRelasjon(
                    minRolleForPerson = FORELDERBARNRELASJONROLLE.BARN,
                    relatertPersonsIdent = PERSONIDENT_FAR,
                    relatertPersonsRolle = FORELDERBARNRELASJONROLLE.FAR
                )
            ),
            dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now()))
        )

        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns
                listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                       RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE))

        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_FAR, listOf(PERSONIDENT_BARN)) } returns
                listOf(RestFagsakDeltager(PERSONIDENT_FAR, FORELDER, SAKS_ID + 50, LØPENDE),
                       RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE))

        every { mockSakClient.hentMinimalRestFagsak(SAKS_ID) } returns lagAktivOrdinærMinimal()
        every { mockSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()

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

        val oppgaveDto = mutableListOf<OppgaveVurderLivshendelseDto>()
        verify(exactly = 2) {
            mockTaskRepository.saveAndFlush(any())
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto))
            mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN))
            mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_FAR, listOf(PERSONIDENT_BARN))
        }

        assertThat(oppgaveDto).allMatch { it.aktørId.contains(PERSONIDENT_MOR) }
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
                                minRolleForPerson = FORELDERBARNRELASJONROLLE.MOR,
                                relatertPersonsIdent = PERSONIDENT_BARN,
                                relatertPersonsRolle = FORELDERBARNRELASJONROLLE.BARN
                        )
                ),
                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now()))
        )

        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns
                listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE))

        every { mockSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivOrdinær()
        every { mockSakClient.hentMinimalRestFagsak(SAKS_ID) } returns lagAktivOrdinærMinimal()

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

        assertThat(oppgaveDto[0].beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_DØDSFALL + ": bruker og barn $PERSONIDENT_BARN")
        assertThat(oppgaveDto[1].beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_UTFLYTTING.format("bruker"))

        assertThat(oppgaveDto).allMatch { it.aktørId.contains(PERSONIDENT_MOR) }
        assertThat(oppgaveDto).allMatch { it.saksId == "$SAKS_ID" }
        assertThat(oppgaveDto).allMatch { it.enhetsId == null }
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
                                minRolleForPerson = FORELDERBARNRELASJONROLLE.BARN,
                                relatertPersonsIdent = PERSONIDENT_MOR,
                                relatertPersonsRolle = FORELDERBARNRELASJONROLLE.MOR
                        ),
                        PdlForeldreBarnRelasjon(
                                minRolleForPerson = FORELDERBARNRELASJONROLLE.BARN,
                                relatertPersonsIdent = PERSONIDENT_FAR,
                                relatertPersonsRolle = FORELDERBARNRELASJONROLLE.FAR
                        )
                ),
                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())),
                fødsel = listOf(Fødsel(LocalDate.now().minusYears(12)))
        )

        every {
            mockPdlClient.hentPerson(
                    PERSONIDENT_MOR,
                    any()
            )
        } returns PdlPersonData(
                forelderBarnRelasjon = listOf(
                        PdlForeldreBarnRelasjon(
                                minRolleForPerson = FORELDERBARNRELASJONROLLE.MOR,
                                relatertPersonsIdent = PERSONIDENT_BARN,
                                relatertPersonsRolle = FORELDERBARNRELASJONROLLE.BARN,
                        )
                ),
        )

        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns
                listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                       RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE))

        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_FAR, listOf(PERSONIDENT_BARN)) } returns
                listOf(RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE))

        every { mockSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()
        every { mockSakClient.hentMinimalRestFagsak(SAKS_ID) } returns lagAktivOrdinærMinimal()

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

        assertThat(oppgaveDto[0].beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_DØDSFALL + ": barn $PERSONIDENT_BARN")
        assertThat(oppgaveDto[1].beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_UTFLYTTING.format("barn $PERSONIDENT_BARN"))

        assertThat(oppgaveDto).allMatch { it.aktørId.contains(PERSONIDENT_MOR) }
        assertThat(oppgaveDto).allMatch { it.saksId == "$SAKS_ID" }
        assertThat(oppgaveDto).allMatch { it.enhetsId == null }
        assertThat(oppgaveDto).allMatch { it.behandlingstema == Behandlingstema.UtvidetBarnetrygd.value }
        assertThat(oppgaveDto).allMatch { it.behandlesAvApplikasjon == BehandlesAvApplikasjon.BA_SAK.applikasjon }
    }

    @Test
    fun `Skal sett riktig beskrivelsestekster for ny dødsfall oppgave`() {
        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns
                listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE))

        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns
                listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                       RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE))


        every { mockSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()
        every { mockSakClient.hentMinimalRestFagsak(SAKS_ID) } returns lagAktivOrdinærMinimal()
        val oppgaveDto = slot<OppgaveVurderLivshendelseDto>()
        every { mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto)) } returns OppgaveResponse(
                oppgaveId = 1
        )

        setupPdlMockForDødsfallshendelse(true, false, false)
        vurderLivshendelseTask.doTask(
                Task.nyTask(
                        type = VurderLivshendelseTask.TASK_STEP_TYPE,
                        payload = objectMapper.writeValueAsString(
                                VurderLivshendelseTaskDTO(
                                        PERSONIDENT_MOR,
                                        DØDSFALL
                                )
                        )
                )
        )

        assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_DØDSFALL + ": bruker")

        setupPdlMockForDødsfallshendelse(true, true, false)
        vurderLivshendelseTask.doTask(
                Task.nyTask(
                        type = VurderLivshendelseTask.TASK_STEP_TYPE,
                        payload = objectMapper.writeValueAsString(
                                VurderLivshendelseTaskDTO(
                                        PERSONIDENT_MOR,
                                        DØDSFALL
                                )
                        )
                )
        )

        assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_DØDSFALL + ": bruker og barn ${PERSONIDENT_BARN}")

        setupPdlMockForDødsfallshendelse(true, true, true)
        vurderLivshendelseTask.doTask(
                Task.nyTask(
                        type = VurderLivshendelseTask.TASK_STEP_TYPE,
                        payload = objectMapper.writeValueAsString(
                                VurderLivshendelseTaskDTO(
                                        PERSONIDENT_MOR,
                                        DØDSFALL
                                )
                        )
                )
        )

        assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_DØDSFALL +
                                                              ": bruker og 2 barn ${PERSONIDENT_BARN} ${PERSONIDENT_BARN2}")

        setupPdlMockForDødsfallshendelse(false, true, false)
        vurderLivshendelseTask.doTask(
                Task.nyTask(
                        type = VurderLivshendelseTask.TASK_STEP_TYPE,
                        payload = objectMapper.writeValueAsString(
                                VurderLivshendelseTaskDTO(
                                        PERSONIDENT_BARN,
                                        DØDSFALL
                                )
                        )
                )
        )

        assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_DØDSFALL +
                                                              ": barn ${PERSONIDENT_BARN}")

        setupPdlMockForDødsfallshendelse(false, true, true)
        vurderLivshendelseTask.doTask(
                Task.nyTask(
                        type = VurderLivshendelseTask.TASK_STEP_TYPE,
                        payload = objectMapper.writeValueAsString(
                                VurderLivshendelseTaskDTO(
                                        PERSONIDENT_BARN,
                                        DØDSFALL
                                )
                        )
                )
        )

        assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_DØDSFALL +
                                                              ": 2 barn ${PERSONIDENT_BARN} ${PERSONIDENT_BARN2}")
    }

    @Test
    fun `Skal sett riktig beskrivelsestekster når oppdater dødsfall oppgave`() {
        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns
                listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE))

        every { mockSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()
        every { mockSakClient.hentMinimalRestFagsak(SAKS_ID) } returns lagAktivOrdinærMinimal()
        val oppgavebeskrivelseSlot = slot<String>()
        every { mockOppgaveClient.oppdaterOppgaveBeskrivelse(any(), capture(oppgavebeskrivelseSlot)) } returns OppgaveResponse(
                oppgaveId = 1
        )

        setupPdlMockForDødsfallshendelse(true, false, false)
        vurderLivshendelseTask.doTask(
                Task.nyTask(
                        type = VurderLivshendelseTask.TASK_STEP_TYPE,
                        payload = objectMapper.writeValueAsString(
                                VurderLivshendelseTaskDTO(
                                        PERSONIDENT_MOR,
                                        DØDSFALL
                                )
                        )
                )
        )

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any()) } returns listOf(
                Oppgave(beskrivelse = VurderLivshendelseTask.BESKRIVELSE_DØDSFALL + ": bruker")
        )

        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns
                listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                       RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE))

        setupPdlMockForDødsfallshendelse(true, true, false)
        vurderLivshendelseTask.doTask(
                Task.nyTask(
                        type = VurderLivshendelseTask.TASK_STEP_TYPE,
                        payload = objectMapper.writeValueAsString(
                                VurderLivshendelseTaskDTO(
                                        PERSONIDENT_BARN,
                                        DØDSFALL
                                )
                        )
                )
        )

        assertThat(oppgavebeskrivelseSlot.captured).isEqualTo(VurderLivshendelseTask.BESKRIVELSE_DØDSFALL + ": bruker og barn ${PERSONIDENT_BARN}")
    }

    private fun setupPdlMockForDødsfallshendelse(morDød: Boolean, barn1Død: Boolean, barn2Død: Boolean) {
        every {
            mockPdlClient.hentPerson(
                    PERSONIDENT_MOR,
                    any()
            )
        } returns PdlPersonData(
                forelderBarnRelasjon = listOf(
                        PdlForeldreBarnRelasjon(
                                minRolleForPerson = FORELDERBARNRELASJONROLLE.MOR,
                                relatertPersonsIdent = PERSONIDENT_BARN,
                                relatertPersonsRolle = FORELDERBARNRELASJONROLLE.BARN
                        ),
                        PdlForeldreBarnRelasjon(
                                minRolleForPerson = FORELDERBARNRELASJONROLLE.MOR,
                                relatertPersonsIdent = PERSONIDENT_BARN2,
                                relatertPersonsRolle = FORELDERBARNRELASJONROLLE.BARN
                        )

                ),
                dødsfall = if (morDød) listOf(Dødsfall(dødsdato = LocalDate.now())) else emptyList(),
                fødsel = listOf(Fødsel(LocalDate.now().minusYears(22)))
        )

        every {
            mockPdlClient.hentPerson(
                    PERSONIDENT_BARN,
                    any()
            )
        } returns PdlPersonData(
                forelderBarnRelasjon = listOf(
                        PdlForeldreBarnRelasjon(
                                minRolleForPerson = FORELDERBARNRELASJONROLLE.BARN,
                                relatertPersonsIdent = PERSONIDENT_MOR,
                                relatertPersonsRolle = FORELDERBARNRELASJONROLLE.MOR
                        ),
                ),
                dødsfall = if (barn1Død) listOf(Dødsfall(dødsdato = LocalDate.now())) else emptyList(),
                fødsel = listOf(Fødsel(LocalDate.now().minusYears(3)))
        )

        every {
            mockPdlClient.hentPerson(
                    PERSONIDENT_BARN2,
                    any()
            )
        } returns PdlPersonData(
                forelderBarnRelasjon = listOf(
                        PdlForeldreBarnRelasjon(
                                minRolleForPerson = FORELDERBARNRELASJONROLLE.BARN,
                                relatertPersonsIdent = PERSONIDENT_MOR,
                                relatertPersonsRolle = FORELDERBARNRELASJONROLLE.MOR
                        ),
                ),
                dødsfall = if (barn2Død) listOf(Dødsfall(dødsdato = LocalDate.now())) else emptyList(),
                fødsel = listOf(Fødsel(LocalDate.now().minusYears(3)))
        )
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
                            BehandlingUnderkategori.ORDINÆR,
                    )
            )
    )

    private fun lagAktivOrdinærMinimal() = RestMinimalFagsak(
            SAKS_ID,
            listOf(
                    RestVisningBehandling(
                            behandlingId = 321,
                            aktiv = true,
                            opprettetTidspunkt = LocalDateTime.now(),
                            status = "TEST",
                            type = "TEST"
                    )
            )
    )

    private fun lagAktivUtvidet() = RestFagsak(
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
                            BehandlingUnderkategori.UTVIDET
                    )
            )
    )

    companion object {

        private val PERSONIDENT_BARN = "12345654321"
        private val PERSONIDENT_BARN2 = "12345654322"
        private val PERSONIDENT_MOR = "12345678901"
        private val PERSONIDENT_FAR = "12345678888"
        private val SAKS_ID = 123L
        private val ENHET_ID = "3049"
    }
}
