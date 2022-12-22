package no.nav.familie.baks.mottak.task

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.config.FeatureToggleService
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.baks.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.baks.mottak.integrasjoner.Dødsfall
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle.BARN
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus.LØPENDE
import no.nav.familie.baks.mottak.integrasjoner.Fødsel
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdBarnetrygdClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.PdlForeldreBarnRelasjon
import no.nav.familie.baks.mottak.integrasjoner.PdlPersonData
import no.nav.familie.baks.mottak.integrasjoner.RestArbeidsfordelingPåBehandling
import no.nav.familie.baks.mottak.integrasjoner.RestFagsak
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.baks.mottak.integrasjoner.RestUtvidetBehandling
import no.nav.familie.baks.mottak.integrasjoner.Sivilstand
import no.nav.familie.baks.mottak.task.VurderLivshendelseType.DØDSFALL
import no.nav.familie.baks.mottak.task.VurderLivshendelseType.SIVILSTAND
import no.nav.familie.baks.mottak.task.VurderLivshendelseType.UTFLYTTING
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkResponse
import no.nav.familie.kontrakter.ba.infotrygd.Stønad
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.kontrakter.felles.personopplysning.SIVILSTAND.GIFT
import no.nav.familie.prosessering.domene.Task
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VurderLivshendelseTaskTest {

    private val mockOppgaveClient: OppgaveClient = mockk()
    private val mockBaSakClient: BaSakClient = mockk()
    private val mockPdlClient: PdlClient = mockk(relaxed = true)
    private val mockInfotrygdClient: InfotrygdBarnetrygdClient = mockk()
    private val mockFeatureToggleService: FeatureToggleService = mockk()

    private val vurderLivshendelseTask =
        VurderLivshendelseTask(
            mockOppgaveClient,
            mockPdlClient,
            mockBaSakClient,
            mockInfotrygdClient,
            mockFeatureToggleService
        )

    @BeforeEach
    internal fun setUp() {
        clearAllMocks()

        every {
            mockOppgaveClient.finnOppgaver(any(), any())
        } returns listOf()

        every {
            mockPdlClient.hentAktørId(PERSONIDENT_MOR)
        } returns PERSONIDENT_MOR + "00"

        every {
            mockPdlClient.hentAktørId(PERSONIDENT_FAR)
        } returns PERSONIDENT_FAR + "00"

        every {
            mockPdlClient.hentAktørId(PERSONIDENT_BARN)
        } returns PERSONIDENT_BARN + "00"

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any()) } returns emptyList()

        every { mockOppgaveClient.opprettVurderLivshendelseOppgave(any()) } returns OppgaveResponse(42)

        every { mockInfotrygdClient.hentVedtak(any()) } returns lagInfotrygdResponse()

        every { mockFeatureToggleService.isEnabled(any(), any()) } returns false
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
            fødsel = listOf(Fødsel(LocalDate.of(1980, 8, 3)))
        )

        listOf(UTFLYTTING, DØDSFALL).forEach {
            vurderLivshendelseTask.doTask(
                Task(
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
            mockOppgaveClient.opprettVurderLivshendelseOppgave(any())
            mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_BARN, listOf())
            mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN))
            mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_FAR, listOf(PERSONIDENT_BARN))
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

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns emptyList()

        listOf(UTFLYTTING, DØDSFALL).forEach {
            vurderLivshendelseTask.doTask(
                Task(
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
            mockOppgaveClient.opprettVurderLivshendelseOppgave(any())
        }

        verify(exactly = 2) {
            mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList())
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

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE)
            )

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_FAR, listOf(PERSONIDENT_BARN)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_FAR, FORELDER, SAKS_ID + 50, LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE)
            )

        every { mockBaSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivOrdinær()

        listOf(UTFLYTTING, DØDSFALL).forEach {
            vurderLivshendelseTask.doTask(
                Task(
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
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto))
            mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN))
            mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_FAR, listOf(PERSONIDENT_BARN))
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
            dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())),
            sivilstand = listOf(
                Sivilstand(
                    type = GIFT,
                    gyldigFraOgMed = LocalDate.now()
                )
            )
        )

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns
            listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE))

        every { mockBaSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()

        listOf(DØDSFALL, UTFLYTTING, SIVILSTAND).forEach {
            vurderLivshendelseTask.doTask(
                Task(
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
        verify(exactly = 3) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto))
        }

        assertThat(oppgaveDto[0].beskrivelse).isEqualTo(DØDSFALL.beskrivelse + ": bruker")
        assertThat(oppgaveDto[1].beskrivelse).isEqualTo(UTFLYTTING.beskrivelse + ": bruker")
        assertThat(oppgaveDto[2].beskrivelse).contains(SIVILSTAND.beskrivelse, "${Year.now()}")

        assertThat(oppgaveDto).allMatch { it.aktørId.contains(PERSONIDENT_MOR) }
        assertThat(oppgaveDto).allMatch { it.saksId == "$SAKS_ID" }
        assertThat(oppgaveDto).allMatch { it.enhetsId == null }
        assertThat(oppgaveDto).allMatch { it.behandlingstema == Behandlingstema.UtvidetBarnetrygd.value }
        assertThat(oppgaveDto).allMatch { it.behandlesAvApplikasjon == null }
    }

    @Test
    fun `Skal kun opprette oppgave for vurdering av sivilstand GIFT når personen har utvidet barnetrygd`() {
        every {
            mockPdlClient.hentPerson(
                any(),
                any()
            )
        } returns PdlPersonData(
            sivilstand = listOf(
                Sivilstand(
                    type = GIFT,
                    gyldigFraOgMed = LocalDate.now()
                )
            )
        )

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns
            listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE))

        every { mockBaSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivOrdinær() andThen lagAktivUtvidet()

        listOf(1, 2).forEach {
            vurderLivshendelseTask.doTask(
                Task(
                    type = VurderLivshendelseTask.TASK_STEP_TYPE,
                    payload = objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_MOR,
                            SIVILSTAND
                        )
                    )
                )
            )
        }

        val oppgaveSlot = slot<OppgaveVurderLivshendelseDto>()
        verify(exactly = 1) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveSlot))
        }

        assertThat(oppgaveSlot.captured.beskrivelse).contains(SIVILSTAND.beskrivelse)
        assertThat(oppgaveSlot.captured.behandlingstema).isEqualTo(Behandlingstema.UtvidetBarnetrygd.value)
    }

    @Test
    fun `Skal bruke sivilstandobjekt fra PdlPersonData med nyest dato`() {
        every {
            mockPdlClient.hentPerson(
                any(),
                any()
            )
        } returns PdlPersonData(
            sivilstand = listOf(
                Sivilstand(type = GIFT),
                Sivilstand(
                    type = GIFT,
                    gyldigFraOgMed = LocalDate.now().minusYears(10)
                ),
                Sivilstand(
                    type = GIFT,
                    bekreftelsesdato = LocalDate.now()
                )
            )
        )

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns
            listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE))

        every { mockBaSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()

        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload = objectMapper.writeValueAsString(
                    VurderLivshendelseTaskDTO(
                        PERSONIDENT_MOR,
                        SIVILSTAND
                    )
                )
            )
        )

        val oppgaveSlot = slot<OppgaveVurderLivshendelseDto>()
        verify(exactly = 1) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveSlot))
        }

        assertThat(oppgaveSlot.captured.beskrivelse).contains(SIVILSTAND.beskrivelse, "${Year.now()}")
    }

    @Test
    fun `Skal ignorere sivilstandhendelser uten dato eller dato eldre enn tidligste vedtak`() {
        val sivilstandUtenDato = Sivilstand(GIFT)
        val sivilstandEldreEnnTidligsteInfotrygdVedtak = Sivilstand(GIFT, LocalDate.now().minusYears(6))
        val sivilstandMedDagensDato = Sivilstand(GIFT, LocalDate.now())
        val sivilstandEldreEnnBasakVedtakMenNyereEnnInfotrygdVedtak = Sivilstand(GIFT, LocalDate.now().minusYears(4))

        every {
            mockPdlClient.hentPerson(any(), "hentperson-sivilstand")
        } returns
            sivilstandUtenDato.data andThen
            sivilstandEldreEnnTidligsteInfotrygdVedtak.data andThen
            sivilstandMedDagensDato.data andThen
            sivilstandEldreEnnBasakVedtakMenNyereEnnInfotrygdVedtak.data

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns
            listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE))

        every { mockBaSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()

        listOf(1, 2, 3, 4).forEach {
            vurderLivshendelseTask.doTask(
                Task(
                    type = VurderLivshendelseTask.TASK_STEP_TYPE,
                    payload = objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_MOR,
                            SIVILSTAND
                        )
                    )
                )
            )
        }

        val oppgaveSlot = mutableListOf<OppgaveVurderLivshendelseDto>()
        verify(exactly = 2) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveSlot))
        }

        assertThat(oppgaveSlot[0].beskrivelse).contains(SIVILSTAND.beskrivelse, "${Year.now()}")
        assertThat(oppgaveSlot[1].beskrivelse).contains(SIVILSTAND.beskrivelse, "${Year.now().minusYears(4)}")
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
                    relatertPersonsRolle = FORELDERBARNRELASJONROLLE.BARN
                )
            )
        )

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE)
            )

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_FAR, listOf(PERSONIDENT_BARN)) } returns
            listOf(RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE))

        every { mockBaSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()

        listOf(DØDSFALL, UTFLYTTING).forEach {
            vurderLivshendelseTask.doTask(
                Task(
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
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto))
        }

        assertThat(oppgaveDto[0].beskrivelse).isEqualTo(DØDSFALL.beskrivelse + ": barn $PERSONIDENT_BARN")
        assertThat(oppgaveDto[1].beskrivelse).isEqualTo(UTFLYTTING.beskrivelse + ": barn $PERSONIDENT_BARN")

        assertThat(oppgaveDto).allMatch { it.aktørId.contains(PERSONIDENT_MOR) }
        assertThat(oppgaveDto).allMatch { it.saksId == "$SAKS_ID" }
        assertThat(oppgaveDto).allMatch { it.enhetsId == null }
        assertThat(oppgaveDto).allMatch { it.behandlingstema == Behandlingstema.UtvidetBarnetrygd.value }
        assertThat(oppgaveDto).allMatch { it.behandlesAvApplikasjon == null }
    }

    @Test
    fun `Skal sett riktig beskrivelsestekster for ny dødsfall oppgave`() {
        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns
            listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE))

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE)
            )

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN2)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN2, BARN, SAKS_ID, LØPENDE)
            )

        every { mockBaSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()
        val oppgaveDto = slot<OppgaveVurderLivshendelseDto>()
        every { mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto)) } returns OppgaveResponse(
            oppgaveId = 1
        )

        setupPdlMockForDødsfallshendelse(true, false, false)
        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload = objectMapper.writeValueAsString(
                    VurderLivshendelseTaskDTO(
                        PERSONIDENT_MOR,
                        DØDSFALL
                    )
                )
            )
        )

        assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(DØDSFALL.beskrivelse + ": bruker")

        setupPdlMockForDødsfallshendelse(true, true, false)
        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload = objectMapper.writeValueAsString(
                    VurderLivshendelseTaskDTO(
                        PERSONIDENT_BARN,
                        DØDSFALL
                    )
                )
            )
        )

        assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(DØDSFALL.beskrivelse + ": barn $PERSONIDENT_BARN")

        setupPdlMockForDødsfallshendelse(true, true, true)
        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload = objectMapper.writeValueAsString(
                    VurderLivshendelseTaskDTO(
                        PERSONIDENT_BARN2,
                        DØDSFALL
                    )
                )
            )
        )

        assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(DØDSFALL.beskrivelse + ": barn $PERSONIDENT_BARN2")
    }

    @Test
    fun `Skal sett riktig beskrivelsestekster når oppdater dødsfall oppgave`() {
        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, emptyList()) } returns
            listOf(RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE))

        every { mockBaSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()
        val oppgavebeskrivelseSlot = slot<String>()
        every {
            mockOppgaveClient.oppdaterOppgaveBeskrivelse(
                any(),
                capture(oppgavebeskrivelseSlot)
            )
        } returns OppgaveResponse(
            oppgaveId = 1
        )

        setupPdlMockForDødsfallshendelse(true, false, false)
        vurderLivshendelseTask.doTask(
            Task(
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
            Oppgave(beskrivelse = DØDSFALL.beskrivelse + ": bruker")
        )

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE)
            )

        setupPdlMockForDødsfallshendelse(true, true, false)
        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload = objectMapper.writeValueAsString(
                    VurderLivshendelseTaskDTO(
                        PERSONIDENT_BARN,
                        DØDSFALL
                    )
                )
            )
        )

        assertThat(oppgavebeskrivelseSlot.captured).isEqualTo(DØDSFALL.beskrivelse + ": bruker og barn $PERSONIDENT_BARN")

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any()) } returns listOf(
            Oppgave(beskrivelse = DØDSFALL.beskrivelse + ": bruker og barn $PERSONIDENT_BARN")
        )

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN2)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN2, BARN, SAKS_ID, LØPENDE)
            )

        setupPdlMockForDødsfallshendelse(true, true, true)
        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload = objectMapper.writeValueAsString(
                    VurderLivshendelseTaskDTO(
                        PERSONIDENT_BARN2,
                        DØDSFALL
                    )
                )
            )
        )

        assertThat(oppgavebeskrivelseSlot.captured).isEqualTo(DØDSFALL.beskrivelse + ": bruker og 2 barn $PERSONIDENT_BARN $PERSONIDENT_BARN2")
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
                )
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
                )
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
                "BEHANDLING_AVSLUTTET",
                "TYPE",
                BehandlingUnderkategori.ORDINÆR
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
                "INNVILGET",
                "BEHANDLING_AVSLUTTET",
                "MIGRERING_FRA_INFOTRYGD",
                BehandlingUnderkategori.UTVIDET
            )
        )
    )

    private fun lagInfotrygdResponse() = InfotrygdSøkResponse(
        bruker = listOf(
            Stønad(iverksattFom = YearMonth.now().minusYears(2).tilSeqFormat),
            Stønad(iverksattFom = YearMonth.now().minusYears(5).tilSeqFormat)
        ),
        barn = listOf()
    )

    companion object {

        private val PERSONIDENT_BARN = "12345654321"
        private val PERSONIDENT_BARN2 = "12345654322"
        private val PERSONIDENT_MOR = "12345678901"
        private val PERSONIDENT_FAR = "12345678888"
        private val SAKS_ID = 123L
        private val ENHET_ID = "3049"
    }

    private val Sivilstand.data: PdlPersonData
        get() = PdlPersonData(sivilstand = listOf(this))

    private val YearMonth.tilSeqFormat: String
        get() = "${999999 - ("$year" + "$monthValue".padStart(2, '0')).toInt()}"
}
