package no.nav.familie.baks.mottak.integrasjoner

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.DevLauncher
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadRepository
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.DokumentInfo
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import no.nav.familie.kontrakter.felles.oppgave.IdentGruppe
import no.nav.familie.kontrakter.felles.oppgave.OppgaveIdentV2
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest(classes = [DevLauncher::class])
@ActiveProfiles("dev", "mock-pdl")
class OppgaveMapperTest(
    @Autowired
    private val journalpostClient: JournalpostClient,
    @Autowired
    private val mockPdlClient: PdlClient,
    @Autowired
    private val barnetrygdSøknadRepository: SøknadRepository,
    @Autowired
    private val kontantstøtteSøknadRepository: KontantstøtteSøknadRepository,
) {
    private val mockEnhetsnummerService: EnhetsnummerService = mockk()
    private val unleashService: UnleashNextMedContextService = mockk()

    private val barnetrygdOppgaveMapper: IOppgaveMapper =
        BarnetrygdOppgaveMapper(
            enhetsnummerService = mockEnhetsnummerService,
            pdlClient = mockPdlClient,
            søknadRepository = barnetrygdSøknadRepository,
        )

    private val kontantstøtteOppgaveMapper: IOppgaveMapper =
        KontantstøtteOppgaveMapper(
            enhetsnummerService = mockEnhetsnummerService,
            pdlClient = mockPdlClient,
            kontantstøtteSøknadRepository = kontantstøtteSøknadRepository,
        )

    @BeforeEach
    fun beforeEach() {
        every { mockEnhetsnummerService.hentEnhetsnummer(any()) } returns "1234"
    }

    @Test
    fun `skal kaste exception dersom dokumentlisten er tom`() {
        Assertions.assertThrows(IllegalStateException::class.java) {
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(dokumenter = listOf()),
            )
        }
    }

    @Test
    fun `skal kaste exception dersom brukerid ikke er satt når oppgavetype er BehandleSak`() {
        Assertions.assertThrows(IllegalStateException::class.java) {
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.BehandleSak,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = null,
                                    brevkode = "",
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                        bruker = null,
                    ),
            )
        }
    }

    @Test
    fun `skal sette brukerid til null dersom bruker ikke finnes i PDL når oppgavetype er Journalføring`() {
        every { mockPdlClient.hentIdenter(any(), any()) } throws IntegrasjonException("Fant ikke person")

        val oppgave =
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("321")
                    .copy(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = null,
                                    brevkode = "NAV 33-00.07",
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                    ),
            )
        assertNull(oppgave.ident)
    }

    @Test
    fun `skal ikke kaste exception selvom brukerid mangler når oppgavetype er Journalføring`() {
        Assertions.assertDoesNotThrow {
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = null,
                                    brevkode = "",
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                        bruker = null,
                    ),
            )
        }
    }

    @Test
    fun `skal sette behandlingstema Ordinær`() {
        val oppgave =
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = null,
                                    brevkode = "NAV 33-00.07",
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                    ),
            )
        assertEquals(Behandlingstema.OrdinærBarnetrygd.value, oppgave.behandlingstema)
    }

    @Test
    fun `skal sette behandlingstema Ordinær uavhengig av journalpost`() {
        val oppgave =
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = null,
                                    brevkode = null,
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                        behandlingstema = "btema",
                    ),
            )
        assertEquals(Behandlingstema.OrdinærBarnetrygd.value, oppgave.behandlingstema)
    }

    @Test
    fun `skal sette behandlingstema EØS`() {
        val oppgave =
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = null,
                                    brevkode = null,
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                        bruker =
                            Bruker(
                                id = "42345678910",
                                type = BrukerIdType.FNR,
                            ),
                    ),
            )
        assertNull(oppgave.behandlingstype)
        assertEquals(Behandlingstema.BarnetrygdEØS.value, oppgave.behandlingstema)
    }

    @Test
    fun `skal sette behandlingstype Utland`() {
        val oppgave =
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = null,
                                    brevkode = null,
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                                DokumentInfo(
                                    tittel = null,
                                    brevkode = "NAV 33-00.15",
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                        behandlingstema = Behandlingstema.OrdinærBarnetrygd.value,
                    ),
            )
        assertNull(oppgave.behandlingstema)
        assertEquals(Behandlingstype.Utland.value, oppgave.behandlingstype)
    }

    @Test
    fun `skal sette beskrivelse til kun tittel på journalpost når beskrivelse i input er null, brevkode på journalpost er satt og dokumentet har tittel`() {
        val oppgave =
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = "Whatever",
                                    brevkode = "kode",
                                    dokumentstatus = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                        behandlingstema = "btema",
                    ),
                beskrivelse = null,
            )
        assertEquals("Whatever", oppgave.beskrivelse)
    }

    @Test
    fun `skal sette beskrivelse til tom når beskrivelse i input er null, brevkode på journalpost er ikke satt og dokumentet har tittel`() {
        val oppgaveUtenBeskrivelse1 =
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = "Whatever",
                                    brevkode = null,
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                        behandlingstema = "btema",
                    ),
            )
        assertEquals("", oppgaveUtenBeskrivelse1.beskrivelse)
    }

    @Test
    fun `skal sette beskrivelse til tom når beskrivelse i input er null, brevkode på journalpost er ikke satt og dokumentet ikke har tittel`() {
        val oppgaveUtenBeskrivelse2 =
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = null,
                                    brevkode = "kode",
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                        behandlingstema = "btema",
                    ),
            )
        assertEquals("", oppgaveUtenBeskrivelse2.beskrivelse)
    }

    @Test
    fun `skal sette beskrivelse til tittel og beskrivelse når beskrivelse i input er satt, brevkode på journalpost er satt og dokumentet har tittel`() {
        val oppgaveUtenBeskrivelse2 =
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = "Whatever",
                                    brevkode = "kode",
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                        behandlingstema = "btema",
                    ),
                "beskrivelsefelt",
            )
        assertEquals("Whatever - beskrivelsefelt", oppgaveUtenBeskrivelse2.beskrivelse)
    }

    @Test
    fun `skal sette beskrivelse til kun beskrivelse fra input når dokumentet mangler tittel`() {
        val oppgaveUtenBeskrivelse2 =
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = null,
                                    brevkode = "kode",
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                        behandlingstema = "btema",
                    ),
                "beskrivelsefelt",
            )
        assertEquals("beskrivelsefelt", oppgaveUtenBeskrivelse2.beskrivelse)
    }

    @Test
    fun `skal sette bruker null hvis Orgnr  er 000000000`() {
        val oppgave =
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        journalforendeEnhet = "5",
                        bruker = Bruker("000000000", BrukerIdType.ORGNR),
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = null,
                                    brevkode = "kode",
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                        behandlingstema = "btema",
                    ),
            )
        assertThat(oppgave.ident).isNull()
    }

    @Test
    fun `skal sette orgnr hvis Orgnr  er satt`() {
        val oppgave =
            barnetrygdOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient
                    .hentJournalpost("123")
                    .copy(
                        journalforendeEnhet = "5",
                        bruker = Bruker("900000000", BrukerIdType.ORGNR),
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    tittel = null,
                                    brevkode = "kode",
                                    dokumentstatus = null,
                                    dokumentvarianter = null,
                                    dokumentInfoId = "id",
                                ),
                            ),
                        behandlingstema = "btema",
                    ),
            )
        assertThat(oppgave.ident).isEqualTo(OppgaveIdentV2("900000000", IdentGruppe.ORGNR))
    }

    @Test
    fun `skal sette behandlingstype NASJONAL dersom tema er KON og søknad ikke er EØS søknad`() {
        val opprettOppgaveRequest =
            kontantstøtteOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient.hentJournalpost("456"),
            )
        assertEquals(Tema.KON, opprettOppgaveRequest.tema)
        assertEquals(Behandlingstype.NASJONAL.value, opprettOppgaveRequest.behandlingstype)
    }

    @Test
    fun `skal sette behandlingstype EØS dersom tema er KON og bruker id er Dnummer`() {
        val opprettOppgaveRequest =
            kontantstøtteOppgaveMapper.tilOpprettOppgaveRequest(
                Oppgavetype.Journalføring,
                journalpostClient.hentJournalpost("456").copy(
                    bruker =
                        Bruker(
                            id = "42345678910",
                            type = BrukerIdType.FNR,
                        ),
                ),
            )
        assertEquals(Tema.KON, opprettOppgaveRequest.tema)
        assertEquals(null, opprettOppgaveRequest.behandlingstema)
        assertEquals(Behandlingstype.EØS.value, opprettOppgaveRequest.behandlingstype)
    }
}
