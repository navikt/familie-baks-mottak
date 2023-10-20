package no.nav.familie.baks.mottak.task

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakIdOgTilknyttetAktørId
import no.nav.familie.baks.mottak.integrasjoner.RestUtvidetBehandling
import no.nav.familie.baks.mottak.integrasjoner.SakClient
import no.nav.familie.baks.mottak.integrasjoner.Sivilstand
import no.nav.familie.baks.mottak.task.VurderLivshendelseType.DØDSFALL
import no.nav.familie.baks.mottak.task.VurderLivshendelseType.SIVILSTAND
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VurderLivshendelseTaskTest {
    private val mockOppgaveClient: OppgaveClient = mockk()
    private val mockSakClient: SakClient = mockk()
    private val mockPdlClient: PdlClient = mockk(relaxed = true)
    private val mockInfotrygdClient: InfotrygdBarnetrygdClient = mockk()

    private val vurderLivshendelseTask =
        VurderLivshendelseTask(mockOppgaveClient, mockPdlClient, mockSakClient, mockInfotrygdClient)

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

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any()) } returns emptyList()

        every { mockOppgaveClient.opprettVurderLivshendelseOppgave(any()) } returns OppgaveResponse(42)

        every { mockInfotrygdClient.hentVedtak(any()) } returns lagInfotrygdResponse()
    }

    @ParameterizedTest
    @EnumSource(
        value = VurderLivshendelseType::class,
        names = ["UTFLYTTING", "DØDSFALL", "SIVILSTAND"],
    )
    fun `Ignorer livshendelser på person som ikke er berørt av hendelse`(livshendelseType: VurderLivshendelseType) {
        every { mockSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) } returns emptyList()
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

        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_BARN,
                            livshendelseType,
                        ),
                    ),
            ),
        )

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
            SIVILSTAND -> {
                every { mockSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(PERSONIDENT_MOR) } returns
                    listOf(RestFagsakIdOgTilknyttetAktørId(PERSONIDENT_MOR + "00", SAKS_ID))
            }
            else -> {
                every { mockSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) } returns
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
                            type = GIFT,
                            gyldigFraOgMed = LocalDate.now(),
                        ),
                    ),
            )

        every { mockSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()

        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_MOR,
                            livshendelseType,
                        ),
                    ),
            ),
        )

        val oppgaveDto = slot<OppgaveVurderLivshendelseDto>()
        verify(exactly = 1) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto))
        }

        when (livshendelseType) {
            SIVILSTAND -> assertThat(oppgaveDto.captured.beskrivelse).contains(SIVILSTAND.beskrivelse, "${Year.now()}")
            else -> assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(livshendelseType.beskrivelse + ": bruker")
        }

        assertThat(oppgaveDto.captured.aktørId).isEqualTo(PERSONIDENT_MOR + "00")
        assertThat(oppgaveDto.captured.saksId).isEqualTo(SAKS_ID.toString())
        assertThat(oppgaveDto.captured.enhetsId).isNull()
        assertThat(oppgaveDto.captured.behandlingstema).isEqualTo(Behandlingstema.UtvidetBarnetrygd.value)
        assertThat(oppgaveDto.captured.behandlesAvApplikasjon).isNull()
    }

    @ParameterizedTest
    @EnumSource(
        value = VurderLivshendelseType::class,
        names = ["UTFLYTTING", "DØDSFALL"],
    )
    fun `Livshendelser på barnet som har sak i ba-sak`(livshendelseType: VurderLivshendelseType) {
        every { mockSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) } returns
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
                            type = GIFT,
                            gyldigFraOgMed = LocalDate.now(),
                        ),
                    ),
            )

        every { mockSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()

        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_BARN,
                            livshendelseType,
                        ),
                    ),
            ),
        )

        val oppgaveDto = slot<OppgaveVurderLivshendelseDto>()
        verify(exactly = 1) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveDto))
        }

        assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(livshendelseType.beskrivelse + ": barn $PERSONIDENT_BARN")

        assertThat(oppgaveDto.captured.aktørId).isEqualTo(PERSONIDENT_MOR + "00")
        assertThat(oppgaveDto.captured.saksId).isEqualTo(SAKS_ID.toString())
        assertThat(oppgaveDto.captured.enhetsId).isNull()
        assertThat(oppgaveDto.captured.behandlingstema).isEqualTo(Behandlingstema.UtvidetBarnetrygd.value)
        assertThat(oppgaveDto.captured.behandlesAvApplikasjon).isNull()
    }

    @Test
    fun `Skal bruke sivilstandobjekt fra PdlPersonData med nyest dato`() {
        every { mockSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(PERSONIDENT_MOR) } returns
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
                        Sivilstand(type = GIFT),
                        Sivilstand(
                            type = GIFT,
                            gyldigFraOgMed = LocalDate.now().minusYears(10),
                        ),
                        Sivilstand(
                            type = GIFT,
                            bekreftelsesdato = LocalDate.now(),
                        ),
                    ),
            )

        every { mockSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()

        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_MOR,
                            SIVILSTAND,
                        ),
                    ),
            ),
        )

        val oppgaveSlot = slot<OppgaveVurderLivshendelseDto>()
        verify(exactly = 1) {
            mockOppgaveClient.opprettVurderLivshendelseOppgave(capture(oppgaveSlot))
        }

        assertThat(oppgaveSlot.captured.beskrivelse).contains(SIVILSTAND.beskrivelse, "${Year.now()}")
    }

    @Test
    fun `Skal ignorere sivilstandhendelser uten dato eller dato eldre enn tidligste vedtak`() {
        every { mockSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(PERSONIDENT_MOR) } returns
            listOf(RestFagsakIdOgTilknyttetAktørId(PERSONIDENT_MOR + "00", SAKS_ID))
        val sivilstandUtenDato = Sivilstand(GIFT)
        val sivilstandEldreEnnTidligsteInfotrygdVedtak = Sivilstand(GIFT, LocalDate.now().minusYears(6))
        val sivilstandMedDagensDato = Sivilstand(GIFT, LocalDate.now())
        val sivilstandEldreEnnBasakVedtakMenNyereEnnInfotrygdVedtak = Sivilstand(GIFT, LocalDate.now().minusYears(4))

        every {
            mockPdlClient.hentPerson(any(), "hentperson-sivilstand", any())
        } returns
            sivilstandUtenDato.data andThen
            sivilstandEldreEnnTidligsteInfotrygdVedtak.data andThen
            sivilstandMedDagensDato.data andThen
            sivilstandEldreEnnBasakVedtakMenNyereEnnInfotrygdVedtak.data

        every { mockSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()

        listOf(1, 2, 3, 4).forEach {
            vurderLivshendelseTask.doTask(
                Task(
                    type = VurderLivshendelseTask.TASK_STEP_TYPE,
                    payload =
                        objectMapper.writeValueAsString(
                            VurderLivshendelseTaskDTO(
                                PERSONIDENT_MOR,
                                SIVILSTAND,
                            ),
                        ),
                ),
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
    fun `Skal sett riktig beskrivelsestekster når oppdater dødsfall oppgave`() {
        every { mockSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) } returns
            listOf(
                RestFagsakIdOgTilknyttetAktørId(PERSONIDENT_MOR + "00", SAKS_ID),
            )

        every { mockSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivUtvidet()
        val oppgavebeskrivelseSlot = slot<String>()
        every { mockOppgaveClient.oppdaterOppgaveBeskrivelse(any(), capture(oppgavebeskrivelseSlot)) } returns
            OppgaveResponse(
                oppgaveId = 1,
            )

        setupPdlMockForDødsfallshendelse(true, false, false)
        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_MOR,
                            DØDSFALL,
                        ),
                    ),
            ),
        )

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any()) } returns
            listOf(
                Oppgave(beskrivelse = DØDSFALL.beskrivelse + ": bruker"),
            )

        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE),
            )

        setupPdlMockForDødsfallshendelse(true, true, false)
        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_BARN,
                            DØDSFALL,
                        ),
                    ),
            ),
        )

        assertThat(oppgavebeskrivelseSlot.captured).isEqualTo(DØDSFALL.beskrivelse + ": bruker og barn $PERSONIDENT_BARN")

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any()) } returns
            listOf(
                Oppgave(beskrivelse = DØDSFALL.beskrivelse + ": bruker og barn $PERSONIDENT_BARN"),
            )

        every { mockSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN2)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN2, BARN, SAKS_ID, LØPENDE),
            )

        setupPdlMockForDødsfallshendelse(true, true, true)
        vurderLivshendelseTask.doTask(
            Task(
                type = VurderLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderLivshendelseTaskDTO(
                            PERSONIDENT_BARN2,
                            DØDSFALL,
                        ),
                    ),
            ),
        )

        assertThat(oppgavebeskrivelseSlot.captured).isEqualTo(DØDSFALL.beskrivelse + ": bruker og 2 barn $PERSONIDENT_BARN $PERSONIDENT_BARN2")
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

    private fun lagAktivUtvidet() =
        RestFagsak(
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
                    BehandlingUnderkategori.UTVIDET,
                ),
            ),
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
        private val SAKS_ID = 123L
        private val ENHET_ID = "3049"
    }

    private val Sivilstand.data: PdlPersonData
        get() = PdlPersonData(sivilstand = listOf(this))

    private val YearMonth.tilSeqFormat: String
        get() = "${999999 - ("$year" + "$monthValue".padStart(2, '0')).toInt()}"
}
