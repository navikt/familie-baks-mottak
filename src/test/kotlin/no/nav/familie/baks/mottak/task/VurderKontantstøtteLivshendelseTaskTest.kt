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
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
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
import no.nav.familie.baks.mottak.integrasjoner.Sivilstand
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VurderKontantstøtteLivshendelseTaskTest {
    private val mockOppgaveClient: OppgaveClient = mockk()
    private val mockKsSakClient: KsSakClient = mockk()
    private val mockPdlClient: PdlClient = mockk(relaxed = true)

    private val vurderKontantstøtteLivshendelseTask =
        VurderKontantstøtteLivshendelseTask(mockOppgaveClient, mockPdlClient, mockKsSakClient)

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

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any(), Tema.KON) } returns emptyList()

        every { mockOppgaveClient.opprettVurderLivshendelseOppgave(any()) } returns OppgaveResponse(42)
    }

    @ParameterizedTest
    @EnumSource(
        value = VurderKontantstøtteLivshendelseType::class,
        names = ["UTFLYTTING", "DØDSFALL"],
    )
    fun `Ignorer livshendelser på person som ikke er berørt av hendelse`(livshendelseType: VurderKontantstøtteLivshendelseType) {
        every { mockKsSakClient.hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(any()) } returns emptyList()
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

        vurderKontantstøtteLivshendelseTask.doTask(
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderKontantstøtteLivshendelseTaskDTO(
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
        value = VurderKontantstøtteLivshendelseType::class,
        names = ["UTFLYTTING", "DØDSFALL"],
    )
    fun `Livshendelser på person som har sak i ks-sak`(livshendelseType: VurderKontantstøtteLivshendelseType) {
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
                            type = GIFT,
                            gyldigFraOgMed = LocalDate.now(),
                        ),
                    ),
            )

        every { mockKsSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivOrdinærFagsak()

        vurderKontantstøtteLivshendelseTask.doTask(
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderKontantstøtteLivshendelseTaskDTO(
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

        assertThat(oppgaveDto.captured.beskrivelse).isEqualTo(livshendelseType.beskrivelse + ": bruker")

        assertThat(oppgaveDto.captured.aktørId).isEqualTo(PERSONIDENT_MOR + "00")
        assertThat(oppgaveDto.captured.saksId).isEqualTo(SAKS_ID.toString())
        assertThat(oppgaveDto.captured.enhetsId).isNull()
        assertThat(oppgaveDto.captured.behandlingstema).isEqualTo(Behandlingstema.Kontantstøtte.value)
        assertThat(oppgaveDto.captured.behandlesAvApplikasjon).isNull()
    }

    @ParameterizedTest
    @EnumSource(
        value = VurderKontantstøtteLivshendelseType::class,
        names = ["UTFLYTTING", "DØDSFALL"],
    )
    fun `Livshendelser på barnet som har sak i ks-sak`(livshendelseType: VurderKontantstøtteLivshendelseType) {
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
                            type = GIFT,
                            gyldigFraOgMed = LocalDate.now(),
                        ),
                    ),
            )

        every { mockKsSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivOrdinærFagsak()

        vurderKontantstøtteLivshendelseTask.doTask(
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderKontantstøtteLivshendelseTaskDTO(
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
        assertThat(oppgaveDto.captured.behandlingstema).isEqualTo(Behandlingstema.Kontantstøtte.value)
        assertThat(oppgaveDto.captured.behandlesAvApplikasjon).isNull()
    }

    @Test
    fun `Skal sett riktig beskrivelsestekster når oppdater dødsfall oppgave`() {
        every { mockKsSakClient.hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(any()) } returns
            listOf(
                RestFagsakIdOgTilknyttetAktørId(PERSONIDENT_MOR + "00", SAKS_ID),
            )

        every { mockKsSakClient.hentRestFagsak(SAKS_ID) } returns lagAktivOrdinærFagsak()
        val oppgavebeskrivelseSlot = slot<String>()
        every { mockOppgaveClient.oppdaterOppgaveBeskrivelse(any(), capture(oppgavebeskrivelseSlot)) } returns
            OppgaveResponse(
                oppgaveId = 1,
            )

        setupPdlMockForDødsfallshendelse(true, false, false)
        vurderKontantstøtteLivshendelseTask.doTask(
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderKontantstøtteLivshendelseTaskDTO(
                            PERSONIDENT_MOR,
                            VurderKontantstøtteLivshendelseType.DØDSFALL,
                        ),
                    ),
            ),
        )

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any(), Tema.KON) } returns
            listOf(
                Oppgave(beskrivelse = VurderKontantstøtteLivshendelseType.DØDSFALL.beskrivelse + ": bruker"),
            )

        every { mockKsSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN, BARN, SAKS_ID, LØPENDE),
            )

        setupPdlMockForDødsfallshendelse(true, true, false)
        vurderKontantstøtteLivshendelseTask.doTask(
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderKontantstøtteLivshendelseTaskDTO(
                            PERSONIDENT_BARN,
                            VurderKontantstøtteLivshendelseType.DØDSFALL,
                        ),
                    ),
            ),
        )

        assertThat(oppgavebeskrivelseSlot.captured).isEqualTo(VurderKontantstøtteLivshendelseType.DØDSFALL.beskrivelse + ": bruker og barn $PERSONIDENT_BARN")

        every { mockOppgaveClient.finnOppgaverPåAktørId(any(), any(), Tema.KON) } returns
            listOf(
                Oppgave(beskrivelse = VurderKontantstøtteLivshendelseType.DØDSFALL.beskrivelse + ": bruker og barn $PERSONIDENT_BARN"),
            )

        every { mockKsSakClient.hentRestFagsakDeltagerListe(PERSONIDENT_MOR, listOf(PERSONIDENT_BARN2)) } returns
            listOf(
                RestFagsakDeltager(PERSONIDENT_MOR, FORELDER, SAKS_ID, LØPENDE),
                RestFagsakDeltager(PERSONIDENT_BARN2, BARN, SAKS_ID, LØPENDE),
            )

        setupPdlMockForDødsfallshendelse(true, true, true)
        vurderKontantstøtteLivshendelseTask.doTask(
            Task(
                type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
                payload =
                    objectMapper.writeValueAsString(
                        VurderKontantstøtteLivshendelseTaskDTO(
                            PERSONIDENT_BARN2,
                            VurderKontantstøtteLivshendelseType.DØDSFALL,
                        ),
                    ),
            ),
        )

        assertThat(oppgavebeskrivelseSlot.captured).isEqualTo(VurderKontantstøtteLivshendelseType.DØDSFALL.beskrivelse + ": bruker og 2 barn $PERSONIDENT_BARN $PERSONIDENT_BARN2")
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

    private fun lagAktivOrdinærFagsak() =
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
                    "FØRSTEGANGSBEHANDLING",
                    BehandlingUnderkategori.ORDINÆR,
                ),
            ),
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
