package no.nav.familie.baks.mottak.task

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.baks.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.baks.mottak.integrasjoner.Dødsfall
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.Fødsel
import no.nav.familie.baks.mottak.integrasjoner.IdentInformasjon
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdBarnetrygdClient
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.PdlForeldreBarnRelasjon
import no.nav.familie.baks.mottak.integrasjoner.PdlPersonData
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakIdOgTilknyttetAktørId
import no.nav.familie.baks.mottak.integrasjoner.RestMinimalFagsak
import no.nav.familie.baks.mottak.integrasjoner.RestVisningBehandling
import no.nav.familie.baks.mottak.integrasjoner.Sivilstand
import no.nav.familie.baks.mottak.task.VurderLivshendelseService.Companion.BEHANDLING_TYPE_MIGRERING
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkResponse
import no.nav.familie.kontrakter.ba.infotrygd.Stønad
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.IdentGruppe
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.kontrakter.felles.personopplysning.SIVILSTAND
import no.nav.familie.prosessering.domene.Task
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VurderLivshendelseServiceTest {
    private val mockOppgaveClient: OppgaveClient = mockk()
    private val mockBaSakClient: BaSakClient = mockk()
    private val mockKsSakClient: KsSakClient = mockk()
    private val mockPdlClient: PdlClient = mockk(relaxed = true)
    private val mockInfotrygdClient: InfotrygdBarnetrygdClient = mockk()

    private val vurderLivshendelseService =
        VurderLivshendelseService(mockOppgaveClient, mockPdlClient, mockBaSakClient, mockKsSakClient, mockInfotrygdClient)

    @BeforeEach
    internal fun setUp() {
        clearAllMocks()

        every {
            mockOppgaveClient.finnOppgaver(any(), any())
        } returns listOf()

        every {
            mockPdlClient.hentAktørId(PERSONIDENT_MOR, any())
        } returns PERSONIDENT_MOR + "00"

        every {
            mockPdlClient.hentAktørId(PERSONIDENT_FAR, any())
        } returns PERSONIDENT_FAR + "00"

        every {
            mockPdlClient.hentAktørId(PERSONIDENT_BARN, any())
        } returns PERSONIDENT_BARN + "00"

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any(), Tema.BAR) } returns emptyList()
        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any(), Tema.KON) } returns emptyList()

        every { mockOppgaveClient.opprettVurderLivshendelseOppgave(any()) } returns OppgaveResponse(42)

        every { mockInfotrygdClient.hentVedtak(any()) } returns lagInfotrygdResponse()

        every { mockPdlClient.hentIdenter(PERSONIDENT_MOR, any()) } returns listOf(IdentInformasjon(PERSONIDENT_MOR, false, IdentGruppe.FOLKEREGISTERIDENT.name))
        every { mockPdlClient.hentIdenter(PERSONIDENT_FAR, any()) } returns listOf(IdentInformasjon(PERSONIDENT_FAR, false, IdentGruppe.FOLKEREGISTERIDENT.name))
        every { mockPdlClient.hentIdenter(PERSONIDENT_BARN, any()) } returns listOf(IdentInformasjon(PERSONIDENT_BARN, false, IdentGruppe.FOLKEREGISTERIDENT.name))
        every { mockPdlClient.hentIdenter(PERSONIDENT_BARN2, any()) } returns listOf(IdentInformasjon(PERSONIDENT_BARN2, false, IdentGruppe.FOLKEREGISTERIDENT.name))
    }

    @ParameterizedTest
    @EnumSource(
        value = VurderLivshendelseType::class,
        names = ["UTFLYTTING", "DØDSFALL", "SIVILSTAND"],
    )
    fun `Ignorer livshendelser på person som ikke er berørt av hendelse`(livshendelseType: VurderLivshendelseType) {
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) } returns emptyList()

        every {
            mockPdlClient.hentPerson(
                PERSONIDENT_BARN,
                any(),
                any(),
            )
        } returns
            PdlPersonData(
                forelderBarnRelasjon =
                    listOf(
                        PdlForeldreBarnRelasjon(
                            minRolleForPerson = FORELDERBARNRELASJONROLLE.BARN,
                            relatertPersonsIdent = PERSONIDENT_MOR,
                            relatertPersonsRolle = FORELDERBARNRELASJONROLLE.MOR,
                        ),
                        PdlForeldreBarnRelasjon(
                            minRolleForPerson = FORELDERBARNRELASJONROLLE.BARN,
                            relatertPersonsIdent = PERSONIDENT_FAR,
                            relatertPersonsRolle = FORELDERBARNRELASJONROLLE.FAR,
                        ),
                    ),
                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())),
                fødsel = listOf(Fødsel(LocalDate.of(1980, 8, 3))),
            )

        val livshendelseTask =
            Task(
                type = VurderBarnetrygdLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_BARN,
                            livshendelseType,
                        ),
                    ),
            )

        vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask, Tema.BAR)

        verify(exactly = 0) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(any())
        }
    }

    @ParameterizedTest
    @EnumSource(
        value = VurderLivshendelseType::class,
        names = ["UTFLYTTING", "DØDSFALL", "SIVILSTAND"],
    )
    fun `Livshendelser på person som har sak i ba-sak`(livshendelseType: VurderLivshendelseType) {
        when (livshendelseType) {
            VurderLivshendelseType.SIVILSTAND -> {
                every { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(PERSONIDENT_MOR) } returns
                    listOf(RestFagsakIdOgTilknyttetAktørId(PERSONIDENT_MOR + "00", SAKS_ID))
            }

            else -> {
                every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) } returns
                    listOf(
                        RestFagsakIdOgTilknyttetAktørId(PERSONIDENT_MOR + "00", SAKS_ID),
                    )
            }
        }

        every {
            mockPdlClient.hentPerson(
                any(),
                any(),
                any(),
            )
        } returns
            PdlPersonData(
                forelderBarnRelasjon =
                    listOf(
                        PdlForeldreBarnRelasjon(
                            minRolleForPerson = FORELDERBARNRELASJONROLLE.MOR,
                            relatertPersonsIdent = PERSONIDENT_BARN,
                            relatertPersonsRolle = FORELDERBARNRELASJONROLLE.BARN,
                        ),
                    ),
                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())),
                sivilstand =
                    listOf(
                        Sivilstand(
                            type = SIVILSTAND.GIFT,
                            gyldigFraOgMed = LocalDate.now(),
                        ),
                    ),
            )

        every { mockBaSakClient.hentMinimalRestFagsak(SAKS_ID) } returns lagMinimalRestFagsakForBA()

        val livshendelseTask =
            Task(
                type = VurderBarnetrygdLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_MOR,
                            livshendelseType,
                        ),
                    ),
            )

        vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask, Tema.BAR)

        val oppgaveDto = slot<OppgaveVurderLivshendelseDto>()
        verify(exactly = 1) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto))
        }

        when (livshendelseType) {
            VurderLivshendelseType.SIVILSTAND -> Assertions.assertThat(oppgaveDto.captured.beskrivelse).contains(VurderLivshendelseType.SIVILSTAND.beskrivelse, "${Year.now()}")
            else -> Assertions.assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(livshendelseType.beskrivelse + ": bruker")
        }

        Assertions.assertThat(oppgaveDto.captured.aktørId).isEqualTo(PERSONIDENT_MOR + "00")
        Assertions.assertThat(oppgaveDto.captured.saksId).isEqualTo(SAKS_ID.toString())
        Assertions.assertThat(oppgaveDto.captured.enhetsId).isNull()
        Assertions.assertThat(oppgaveDto.captured.behandlingstema).isEqualTo(Behandlingstema.UtvidetBarnetrygd.value)
        Assertions.assertThat(oppgaveDto.captured.behandlesAvApplikasjon).isNull()
        Assertions.assertThat(oppgaveDto.captured.tema).isEqualTo(Tema.BAR)
    }

    @ParameterizedTest
    @EnumSource(
        value = VurderLivshendelseType::class,
        names = ["UTFLYTTING", "DØDSFALL"],
    )
    fun `Livshendelser på person som har sak i ks-sak`(livshendelseType: VurderLivshendelseType) {
        every { mockKsSakClient.hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(any()) } returns
            listOf(
                RestFagsakIdOgTilknyttetAktørId(PERSONIDENT_MOR + "00", SAKS_ID),
            )

        every {
            mockPdlClient.hentPerson(
                any(),
                any(),
                any(),
            )
        } returns
            PdlPersonData(
                forelderBarnRelasjon =
                    listOf(
                        PdlForeldreBarnRelasjon(
                            minRolleForPerson = FORELDERBARNRELASJONROLLE.MOR,
                            relatertPersonsIdent = PERSONIDENT_BARN,
                            relatertPersonsRolle = FORELDERBARNRELASJONROLLE.BARN,
                        ),
                    ),
                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())),
                sivilstand =
                    listOf(
                        Sivilstand(
                            type = SIVILSTAND.GIFT,
                            gyldigFraOgMed = LocalDate.now(),
                        ),
                    ),
            )

        every { mockKsSakClient.hentMinimalRestFagsak(SAKS_ID) } returns lagMinimalRestFagsakForKS()

        val livshendelseTask =
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_MOR,
                            livshendelseType,
                        ),
                    ),
            )

        vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask, Tema.KON)

        val oppgaveDto = slot<OppgaveVurderLivshendelseDto>()
        verify(exactly = 1) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto))
        }

        Assertions.assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(livshendelseType.beskrivelse + ": bruker")
        Assertions.assertThat(oppgaveDto.captured.aktørId).isEqualTo(PERSONIDENT_MOR + "00")
        Assertions.assertThat(oppgaveDto.captured.saksId).isEqualTo(SAKS_ID.toString())
        Assertions.assertThat(oppgaveDto.captured.enhetsId).isNull()
        Assertions.assertThat(oppgaveDto.captured.behandlingstema).isEqualTo(Behandlingstema.Kontantstøtte.value)
        Assertions.assertThat(oppgaveDto.captured.behandlesAvApplikasjon).isNull()
        Assertions.assertThat(oppgaveDto.captured.tema).isEqualTo(Tema.KON)
    }

    @ParameterizedTest
    @EnumSource(
        value = VurderLivshendelseType::class,
        names = ["UTFLYTTING", "DØDSFALL"],
    )
    fun `Livshendelser på barnet som har sak i ba-sak`(livshendelseType: VurderLivshendelseType) {
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) } returns
            listOf(
                RestFagsakIdOgTilknyttetAktørId(PERSONIDENT_MOR + "00", SAKS_ID),
            )

        every {
            mockPdlClient.hentPerson(
                any(),
                any(),
                any(),
            )
        } returns
            PdlPersonData(
                forelderBarnRelasjon =
                    listOf(
                        PdlForeldreBarnRelasjon(
                            minRolleForPerson = FORELDERBARNRELASJONROLLE.MOR,
                            relatertPersonsIdent = PERSONIDENT_BARN,
                            relatertPersonsRolle = FORELDERBARNRELASJONROLLE.BARN,
                        ),
                    ),
                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())),
                sivilstand =
                    listOf(
                        Sivilstand(
                            type = SIVILSTAND.GIFT,
                            gyldigFraOgMed = LocalDate.now(),
                        ),
                    ),
            )

        every { mockBaSakClient.hentMinimalRestFagsak(SAKS_ID) } returns lagMinimalRestFagsakForBA()

        val livshendelseTask =
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_BARN,
                            livshendelseType,
                        ),
                    ),
            )

        vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask, Tema.BAR)

        val oppgaveDto = slot<OppgaveVurderLivshendelseDto>()
        verify(exactly = 1) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto))
        }

        Assertions.assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(livshendelseType.beskrivelse + ": barn $PERSONIDENT_BARN")

        Assertions.assertThat(oppgaveDto.captured.aktørId).isEqualTo(PERSONIDENT_MOR + "00")
        Assertions.assertThat(oppgaveDto.captured.saksId).isEqualTo(SAKS_ID.toString())
        Assertions.assertThat(oppgaveDto.captured.enhetsId).isNull()
        Assertions.assertThat(oppgaveDto.captured.behandlingstema).isEqualTo(Behandlingstema.UtvidetBarnetrygd.value)
        Assertions.assertThat(oppgaveDto.captured.behandlesAvApplikasjon).isNull()
        Assertions.assertThat(oppgaveDto.captured.tema).isEqualTo(Tema.BAR)
    }

    @ParameterizedTest
    @EnumSource(
        value = VurderLivshendelseType::class,
        names = ["UTFLYTTING", "DØDSFALL"],
    )
    fun `Livshendelser på barnet som har sak i ks-sak`(livshendelseType: VurderLivshendelseType) {
        every { mockKsSakClient.hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(any()) } returns
            listOf(
                RestFagsakIdOgTilknyttetAktørId(PERSONIDENT_MOR + "00", SAKS_ID),
            )

        every {
            mockPdlClient.hentPerson(
                any(),
                any(),
                any(),
            )
        } returns
            PdlPersonData(
                forelderBarnRelasjon =
                    listOf(
                        PdlForeldreBarnRelasjon(
                            minRolleForPerson = FORELDERBARNRELASJONROLLE.MOR,
                            relatertPersonsIdent = PERSONIDENT_BARN,
                            relatertPersonsRolle = FORELDERBARNRELASJONROLLE.BARN,
                        ),
                    ),
                dødsfall = listOf(Dødsfall(dødsdato = LocalDate.now())),
                sivilstand =
                    listOf(
                        Sivilstand(
                            type = SIVILSTAND.GIFT,
                            gyldigFraOgMed = LocalDate.now(),
                        ),
                    ),
            )

        every { mockKsSakClient.hentMinimalRestFagsak(SAKS_ID) } returns lagMinimalRestFagsakForBA()

        val livshendelseTask =
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_BARN,
                            livshendelseType,
                        ),
                    ),
            )

        vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask, Tema.KON)

        val oppgaveDto = slot<OppgaveVurderLivshendelseDto>()
        verify(exactly = 1) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto))
        }

        Assertions.assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(livshendelseType.beskrivelse + ": barn $PERSONIDENT_BARN")

        Assertions.assertThat(oppgaveDto.captured.aktørId).isEqualTo(PERSONIDENT_MOR + "00")
        Assertions.assertThat(oppgaveDto.captured.saksId).isEqualTo(SAKS_ID.toString())
        Assertions.assertThat(oppgaveDto.captured.enhetsId).isNull()
        Assertions.assertThat(oppgaveDto.captured.behandlingstema).isEqualTo(Behandlingstema.Kontantstøtte.value)
        Assertions.assertThat(oppgaveDto.captured.behandlesAvApplikasjon).isNull()
        Assertions.assertThat(oppgaveDto.captured.tema).isEqualTo(Tema.KON)
    }

    @Test
    fun `Skal bruke sivilstandobjekt fra PdlPersonData med nyest dato`() {
        every { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(PERSONIDENT_MOR) } returns
            listOf(RestFagsakIdOgTilknyttetAktørId(PERSONIDENT_MOR + "00", SAKS_ID))
        every {
            mockPdlClient.hentPerson(
                any(),
                any(),
                any(),
            )
        } returns
            PdlPersonData(
                sivilstand =
                    listOf(
                        Sivilstand(type = SIVILSTAND.GIFT),
                        Sivilstand(
                            type = SIVILSTAND.GIFT,
                            gyldigFraOgMed = LocalDate.now().minusYears(10),
                        ),
                        Sivilstand(
                            type = SIVILSTAND.GIFT,
                            bekreftelsesdato = LocalDate.now(),
                        ),
                    ),
            )

        every { mockBaSakClient.hentMinimalRestFagsak(SAKS_ID) } returns lagMinimalRestFagsakForBA()

        val livshendelseTask =
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_MOR,
                            VurderLivshendelseType.SIVILSTAND,
                        ),
                    ),
            )

        vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask, Tema.BAR)

        val oppgaveSlot = slot<OppgaveVurderLivshendelseDto>()
        verify(exactly = 1) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveSlot))
        }

        Assertions.assertThat(oppgaveSlot.captured.beskrivelse).contains(VurderLivshendelseType.SIVILSTAND.beskrivelse, "${Year.now()}")
    }

    @Test
    fun `Skal ignorere sivilstandhendelser uten dato eller dato eldre enn tidligste vedtak`() {
        every { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(PERSONIDENT_MOR) } returns
            listOf(RestFagsakIdOgTilknyttetAktørId(PERSONIDENT_MOR + "00", SAKS_ID))
        val sivilstandUtenDato = Sivilstand(SIVILSTAND.GIFT)
        val sivilstandEldreEnnTidligsteInfotrygdVedtak = Sivilstand(SIVILSTAND.GIFT, LocalDate.now().minusYears(6))
        val sivilstandMedDagensDato = Sivilstand(SIVILSTAND.GIFT, LocalDate.now())
        val sivilstandEldreEnnBasakVedtakMenNyereEnnInfotrygdVedtak = Sivilstand(SIVILSTAND.GIFT, LocalDate.now().minusYears(4))

        every {
            mockPdlClient.hentPerson(any(), "hentperson-sivilstand", any())
        } returns
            sivilstandUtenDato.data andThen
            sivilstandEldreEnnTidligsteInfotrygdVedtak.data andThen
            sivilstandMedDagensDato.data andThen
            sivilstandEldreEnnBasakVedtakMenNyereEnnInfotrygdVedtak.data

        every { mockBaSakClient.hentMinimalRestFagsak(SAKS_ID) } returns lagMinimalRestFagsakForBA()

        listOf(1, 2, 3, 4).forEach {
            val livshendelseTask =
                Task(
                    type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                    payload =
                        objectMapper.writeValueAsString(
                            VurderLivshendelseTaskDTO(
                                PERSONIDENT_MOR,
                                VurderLivshendelseType.SIVILSTAND,
                            ),
                        ),
                )

            vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask, Tema.BAR)
        }

        val oppgaveSlot = mutableListOf<OppgaveVurderLivshendelseDto>()
        verify(exactly = 2) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveSlot))
        }

        Assertions.assertThat(oppgaveSlot[0].beskrivelse).contains(VurderLivshendelseType.SIVILSTAND.beskrivelse, "${Year.now()}")
        Assertions.assertThat(oppgaveSlot[1].beskrivelse).contains(VurderLivshendelseType.SIVILSTAND.beskrivelse, "${Year.now().minusYears(4)}")
    }

    @Test
    fun `Skal sett riktig beskrivelsestekster når oppdater dødsfall oppgave`() {
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) } returns
            listOf(
                RestFagsakIdOgTilknyttetAktørId(PERSONIDENT_MOR + "00", SAKS_ID),
            )

        every { mockBaSakClient.hentMinimalRestFagsak(SAKS_ID) } returns lagMinimalRestFagsakForBA()
        val oppgavebeskrivelseSlot = slot<String>()
        every { mockOppgaveClient.oppdaterOppgaveBeskrivelse(any(), capture(oppgavebeskrivelseSlot)) } returns
            OppgaveResponse(
                oppgaveId = 1,
            )

        setupPdlMockForDødsfallshendelse(true, false, false)

        val livshendelseTask =
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_MOR,
                            VurderLivshendelseType.DØDSFALL,
                        ),
                    ),
            )

        vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask, Tema.BAR)

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any(), Tema.BAR) } returns
            listOf(
                Oppgave(beskrivelse = VurderLivshendelseType.DØDSFALL.beskrivelse + ": bruker"),
            )

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_MOR, FagsakDeltagerRolle.FORELDER, SAKS_ID, FagsakStatus.LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN, FagsakDeltagerRolle.BARN, SAKS_ID, FagsakStatus.LØPENDE),
            )

        setupPdlMockForDødsfallshendelse(true, true, false)

        val livshendelseTask2 =
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_BARN,
                            VurderLivshendelseType.DØDSFALL,
                        ),
                    ),
            )

        vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask2, Tema.BAR)

        Assertions.assertThat(oppgavebeskrivelseSlot.captured).isEqualTo(VurderLivshendelseType.DØDSFALL.beskrivelse + ": bruker og barn $PERSONIDENT_BARN")

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any(), Tema.BAR) } returns
            listOf(
                Oppgave(beskrivelse = VurderLivshendelseType.DØDSFALL.beskrivelse + ": bruker og barn $PERSONIDENT_BARN"),
            )

        every { mockBaSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN2)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_MOR, FagsakDeltagerRolle.FORELDER, SAKS_ID, FagsakStatus.LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN2, FagsakDeltagerRolle.BARN, SAKS_ID, FagsakStatus.LØPENDE),
            )

        setupPdlMockForDødsfallshendelse(true, true, true)
        val livshendelseTask3 =
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_BARN2,
                            VurderLivshendelseType.DØDSFALL,
                        ),
                    ),
            )

        vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask3, Tema.BAR)

        Assertions.assertThat(oppgavebeskrivelseSlot.captured).isEqualTo(VurderLivshendelseType.DØDSFALL.beskrivelse + ": bruker og 2 barn $PERSONIDENT_BARN $PERSONIDENT_BARN2")
    }

    @Test
    fun `Skal ignorere hendelse hvis ident ikke finnes i PDL`() {
        val livshendelseTask =
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            UKJENT_PERSONIDENT,
                            VurderLivshendelseType.SIVILSTAND,
                        ),
                    ),
            )

        vurderLivshendelseService.vurderLivshendelseOppgave(livshendelseTask, Tema.BAR)

        val oppgaveSlot = mutableListOf<OppgaveVurderLivshendelseDto>()
        verify(exactly = 0) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(any())
            mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any())
        }
    }

    private fun setupPdlMockForDødsfallshendelse(
        morDød: Boolean,
        barn1Død: Boolean,
        barn2Død: Boolean,
    ) {
        every {
            mockPdlClient.hentPerson(
                PERSONIDENT_MOR,
                any(),
                any(),
            )
        } returns
            PdlPersonData(
                forelderBarnRelasjon =
                    listOf(
                        PdlForeldreBarnRelasjon(
                            minRolleForPerson = FORELDERBARNRELASJONROLLE.MOR,
                            relatertPersonsIdent = PERSONIDENT_BARN,
                            relatertPersonsRolle = FORELDERBARNRELASJONROLLE.BARN,
                        ),
                        PdlForeldreBarnRelasjon(
                            minRolleForPerson = FORELDERBARNRELASJONROLLE.MOR,
                            relatertPersonsIdent = PERSONIDENT_BARN2,
                            relatertPersonsRolle = FORELDERBARNRELASJONROLLE.BARN,
                        ),
                    ),
                dødsfall = if (morDød) listOf(Dødsfall(dødsdato = LocalDate.now())) else emptyList(),
                fødsel = listOf(Fødsel(LocalDate.now().minusYears(22))),
            )

        every {
            mockPdlClient.hentPerson(
                PERSONIDENT_BARN,
                any(),
                any(),
            )
        } returns
            PdlPersonData(
                forelderBarnRelasjon =
                    listOf(
                        PdlForeldreBarnRelasjon(
                            minRolleForPerson = FORELDERBARNRELASJONROLLE.BARN,
                            relatertPersonsIdent = PERSONIDENT_MOR,
                            relatertPersonsRolle = FORELDERBARNRELASJONROLLE.MOR,
                        ),
                    ),
                dødsfall = if (barn1Død) listOf(Dødsfall(dødsdato = LocalDate.now())) else emptyList(),
                fødsel = listOf(Fødsel(LocalDate.now().minusYears(3))),
            )

        every {
            mockPdlClient.hentPerson(
                PERSONIDENT_BARN2,
                any(),
                any(),
            )
        } returns
            PdlPersonData(
                forelderBarnRelasjon =
                    listOf(
                        PdlForeldreBarnRelasjon(
                            minRolleForPerson = FORELDERBARNRELASJONROLLE.BARN,
                            relatertPersonsIdent = PERSONIDENT_MOR,
                            relatertPersonsRolle = FORELDERBARNRELASJONROLLE.MOR,
                        ),
                    ),
                dødsfall = if (barn2Død) listOf(Dødsfall(dødsdato = LocalDate.now())) else emptyList(),
                fødsel = listOf(Fødsel(LocalDate.now().minusYears(3))),
            )
    }

    private fun lagMinimalRestFagsakForBA() =
        RestMinimalFagsak(
            id = SAKS_ID,
            behandlinger =
                listOf(
                    lagRestVisningBehandling(underkategori = BehandlingUnderkategori.UTVIDET),
                ),
        )

    private fun lagMinimalRestFagsakForKS() =
        RestMinimalFagsak(
            id = SAKS_ID,
            behandlinger =
                listOf(
                    lagRestVisningBehandling(underkategori = null),
                ),
        )

    private fun lagRestVisningBehandling(underkategori: BehandlingUnderkategori?) =
        RestVisningBehandling(
            aktiv = true,
            behandlingId = 321,
            kategori = BehandlingKategori.NASJONAL,
            opprettetTidspunkt = LocalDateTime.now(),
            resultat = "INNVILGET",
            status = "AVSLUTTET",
            type = BEHANDLING_TYPE_MIGRERING,
            underkategori = underkategori,
        )

    private fun lagInfotrygdResponse() =
        InfotrygdSøkResponse(
            bruker =
                listOf(
                    Stønad(iverksattFom = YearMonth.now().minusYears(2).tilSeqFormat),
                    Stønad(iverksattFom = YearMonth.now().minusYears(5).tilSeqFormat),
                ),
            barn = listOf(),
        )

    companion object {
        private val PERSONIDENT_BARN = "12345654321"
        private val PERSONIDENT_BARN2 = "12345654322"
        private val PERSONIDENT_MOR = "12345678901"
        private val PERSONIDENT_FAR = "12345678888"
        private val UKJENT_PERSONIDENT = "12345666666"
        private val SAKS_ID = 123L
    }

    private val Sivilstand.data: PdlPersonData
        get() = PdlPersonData(sivilstand = listOf(this))

    private val YearMonth.tilSeqFormat: String
        get() = "${999999 - ("$year" + "$monthValue".padStart(2, '0')).toInt()}"
}
