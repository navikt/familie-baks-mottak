package no.nav.familie.baks.mottak.journalføring

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.integrasjoner.ArbeidsfordelingClient
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.BarnetrygdOppgaveMapper
import no.nav.familie.baks.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.baks.mottak.integrasjoner.BehandlingStatus
import no.nav.familie.baks.mottak.integrasjoner.BehandlingType
import no.nav.familie.baks.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.Fødsel
import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.baks.mottak.integrasjoner.PdlPersonData
import no.nav.familie.baks.mottak.integrasjoner.RestMinimalFagsak
import no.nav.familie.baks.mottak.integrasjoner.RestVisningBehandling
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.arbeidsfordeling.Enhet
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.DokumentInfo
import no.nav.familie.kontrakter.felles.journalpost.Dokumentstatus
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AutomatiskJournalføringBarnetrygdServiceTest {
    private val mockedBaSakClient: BaSakClient = mockk()
    private val mockedArbeidsfordelingClient: ArbeidsfordelingClient = mockk()
    private val mockedAdressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService = mockk()
    private val mockedJournalpostBrukerService: JournalpostBrukerService = mockk()
    private val mockedBarnetrygdOppgaveMapper: BarnetrygdOppgaveMapper = mockk()
    private val mockedPdlClientService: PdlClientService = mockk()

    private val automatiskJournalføringBarnetrygdService: AutomatiskJournalføringBarnetrygdService =
        AutomatiskJournalføringBarnetrygdService(
            baSakClient = mockedBaSakClient,
            arbeidsfordelingClient = mockedArbeidsfordelingClient,
            adressebeskyttelesesgraderingService = mockedAdressebeskyttelesesgraderingService,
            journalpostBrukerService = mockedJournalpostBrukerService,
            barnetrygdOppgaveMapper = mockedBarnetrygdOppgaveMapper,
            pdlClientService = mockedPdlClientService,
        )

    @BeforeEach
    internal fun setUp() {
        every { mockedBarnetrygdOppgaveMapper.hentBehandlingstype(any()) } returns Behandlingstype.NASJONAL
        every { mockedPdlClientService.hentPerson(any(), "hentperson-fødselsdato", any()) } returns
            PdlPersonData(fødsel = listOf(Fødsel(LocalDate.now().minusYears(30))))
    }

    @Test
    fun `skal ikke automatisk journalføre journalpost om journalposten ikke er barnetrygd`() {
        // Arrange
        val journalpost =
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                dokumenter =
                    listOf(
                        DokumentInfo(
                            brevkode = "ikke_barnetrygd",
                            tittel = "Søknad",
                            dokumentstatus = Dokumentstatus.FERDIGSTILT,
                            dokumentvarianter = null,
                            dokumentInfoId = "id",
                        ),
                    ),
            )

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalpost om søknaden ikke er digital`() {
        // Arrange
        val journalpost =
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                kanal = "IKKE_NAV_NO",
                dokumenter =
                    listOf(
                        DokumentInfo(
                            brevkode = "NAV 33-00.07",
                            tittel = "Søknad",
                            dokumentstatus = Dokumentstatus.FERDIGSTILT,
                            dokumentvarianter = emptyList(),
                            dokumentInfoId = "id",
                        ),
                    ),
            )

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalpost hvor enhet på behandlede ident ikke skal bli automatisk journalført`() {
        // Arrange
        val identifikator = "123"
        val fagsakId = 1L

        val journalpost =
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker =
                    Bruker(
                        id = identifikator,
                        type = BrukerIdType.FNR,
                    ),
                kanal = "NAV_NO",
                dokumenter =
                    listOf(
                        DokumentInfo(
                            brevkode = "NAV 33-00.07",
                            tittel = "Søknad",
                            dokumentstatus = Dokumentstatus.FERDIGSTILT,
                            dokumentvarianter = emptyList(),
                            dokumentInfoId = "id",
                        ),
                    ),
            )

        every {
            mockedJournalpostBrukerService.tilPersonIdent(
                bruker = journalpost.bruker!!,
                tema = Tema.BAR,
            )
        } returns identifikator

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpostBruker(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.BAR,
                behandlingstype = any(),
            )
        } returns
            Enhet(
                enhetId = "4863",
                enhetNavn = "enhetNavn",
            )

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalpost om åpen behandling finnes i fagsak`() {
        // Arrange
        val identifikator = "123"
        val fagsakId = 1L

        val journalpost =
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker =
                    Bruker(
                        id = identifikator,
                        type = BrukerIdType.FNR,
                    ),
                kanal = "NAV_NO",
                dokumenter =
                    listOf(
                        DokumentInfo(
                            brevkode = "NAV 33-00.07",
                            tittel = "Søknad",
                            dokumentstatus = Dokumentstatus.FERDIGSTILT,
                            dokumentvarianter = emptyList(),
                            dokumentInfoId = "id",
                        ),
                    ),
            )

        every {
            mockedJournalpostBrukerService.tilPersonIdent(
                bruker = journalpost.bruker!!,
                tema = Tema.BAR,
            )
        } returns identifikator

        every { mockedBaSakClient.hentFagsaknummerPåPersonident(any()) } returns fagsakId

        every {
            mockedBaSakClient.hentMinimalRestFagsak(
                fagsakId = fagsakId,
            )
        } returns
            RestMinimalFagsak(
                id = fagsakId,
                behandlinger =
                    listOf(
                        RestVisningBehandling(
                            behandlingId = 12931230L,
                            opprettetTidspunkt = LocalDateTime.now(),
                            kategori = BehandlingKategori.NASJONAL,
                            aktiv = true,
                            underkategori = BehandlingUnderkategori.ORDINÆR,
                            status = BehandlingStatus.UTREDES,
                            type = BehandlingType.FØRSTEGANGSBEHANDLING,
                        ),
                    ),
                status = FagsakStatus.LØPENDE,
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpostBruker(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.BAR,
                behandlingstype = any(),
            )
        } returns
            Enhet(
                enhetId = "enhetId",
                enhetNavn = "enhetNavn",
            )

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalpost om det finnes en tilknyttet person med adressebeskyttelse`() {
        // Arrange
        val journalpost =
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns true

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal automatisk journalføre journalpost hvis søker er en strengt fortrolig person`() {
        // Arrange
        val identifikator = "123"
        val fagsakId = 1L

        val journalpost =
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker =
                    Bruker(
                        id = identifikator,
                        type = BrukerIdType.FNR,
                    ),
                kanal = "NAV_NO",
                dokumenter =
                    listOf(
                        DokumentInfo(
                            brevkode = "NAV 33-00.07",
                            tittel = "Søknad",
                            dokumentstatus = Dokumentstatus.FERDIGSTILT,
                            dokumentvarianter = emptyList(),
                            dokumentInfoId = "id",
                        ),
                    ),
            )

        every {
            mockedJournalpostBrukerService.tilPersonIdent(
                bruker = journalpost.bruker!!,
                tema = Tema.BAR,
            )
        } returns identifikator

        every { mockedBaSakClient.hentFagsaknummerPåPersonident(any()) } returns fagsakId

        every {
            mockedBaSakClient.hentMinimalRestFagsak(
                fagsakId = fagsakId,
            )
        } returns
            RestMinimalFagsak(
                id = fagsakId,
                behandlinger = listOf(),
                status = FagsakStatus.LØPENDE,
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpostBruker(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns true

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.BAR,
                behandlingstype = any(),
            )
        } returns
            Enhet(
                enhetId = "enhetId",
                enhetNavn = "enhetNavn",
            )

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isTrue()
    }

    @Test
    fun `skal ikke automatisk journalføre journalpost hvis søker ikke er en strengt fortrolig person men barna er`() {
        // Arrange
        val identifikator = "123"
        val fagsakId = 1L

        val journalpost =
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker =
                    Bruker(
                        id = identifikator,
                        type = BrukerIdType.FNR,
                    ),
                kanal = "NAV_NO",
                dokumenter =
                    listOf(
                        DokumentInfo(
                            brevkode = "NAV 33-00.07",
                            tittel = "Søknad",
                            dokumentstatus = Dokumentstatus.FERDIGSTILT,
                            dokumentvarianter = emptyList(),
                            dokumentInfoId = "id",
                        ),
                    ),
            )

        every {
            mockedJournalpostBrukerService.tilPersonIdent(
                bruker = journalpost.bruker!!,
                tema = Tema.BAR,
            )
        } returns identifikator

        every { mockedBaSakClient.hentFagsaknummerPåPersonident(any()) } returns fagsakId

        every {
            mockedBaSakClient.hentMinimalRestFagsak(
                fagsakId = fagsakId,
            )
        } returns
            RestMinimalFagsak(
                id = fagsakId,
                behandlinger = listOf(),
                status = FagsakStatus.LØPENDE,
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns true

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpostBruker(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.BAR,
                behandlingstype = any(),
            )
        } returns
            Enhet(
                enhetId = "enhetId",
                enhetNavn = "enhetNavn",
            )

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalpost hvis det er orgnummer`() {
        // Arrange
        val identifikator = "123"
        val fagsakId = 1L

        val journalpost =
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker =
                    Bruker(
                        id = identifikator,
                        type = BrukerIdType.ORGNR,
                    ),
                kanal = "NAV_NO",
                dokumenter =
                    listOf(
                        DokumentInfo(
                            brevkode = "NAV 33-00.07",
                            tittel = "Søknad",
                            dokumentstatus = Dokumentstatus.FERDIGSTILT,
                            dokumentvarianter = emptyList(),
                            dokumentInfoId = "id",
                        ),
                    ),
            )

        every {
            mockedJournalpostBrukerService.tilPersonIdent(
                bruker = journalpost.bruker!!,
                tema = Tema.BAR,
            )
        } returns identifikator

        every { mockedBaSakClient.hentFagsaknummerPåPersonident(any()) } returns fagsakId

        every {
            mockedBaSakClient.hentMinimalRestFagsak(
                fagsakId = fagsakId,
            )
        } returns
            RestMinimalFagsak(
                id = fagsakId,
                behandlinger = listOf(),
                status = FagsakStatus.LØPENDE,
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpostBruker(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.BAR,
                behandlingstype = any(),
            )
        } returns
            Enhet(
                enhetId = "enhetId",
                enhetNavn = "enhetNavn",
            )

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal automatisk journalføre journalpost hvis enhet er 2103`() {
        // Arrange
        val identifikator = "123"
        val fagsakId = 1L

        val journalpost =
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker =
                    Bruker(
                        id = identifikator,
                        type = BrukerIdType.FNR,
                    ),
                kanal = "NAV_NO",
                dokumenter =
                    listOf(
                        DokumentInfo(
                            brevkode = "NAV 33-00.07",
                            tittel = "Søknad",
                            dokumentstatus = Dokumentstatus.FERDIGSTILT,
                            dokumentvarianter = emptyList(),
                            dokumentInfoId = "id",
                        ),
                    ),
            )

        every {
            mockedJournalpostBrukerService.tilPersonIdent(
                bruker = journalpost.bruker!!,
                tema = Tema.BAR,
            )
        } returns identifikator

        every { mockedBaSakClient.hentFagsaknummerPåPersonident(any()) } returns fagsakId

        every {
            mockedBaSakClient.hentMinimalRestFagsak(
                fagsakId = fagsakId,
            )
        } returns
            RestMinimalFagsak(
                id = fagsakId,
                behandlinger = listOf(),
                status = FagsakStatus.LØPENDE,
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpostBruker(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns true

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.BAR,
                behandlingstype = any(),
            )
        } returns
            Enhet(
                enhetId = "2103",
                enhetNavn = "Vikafossen",
            )

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isTrue()
    }

    @Test
    fun `skal automatisk journalføre journalpost`() {
        // Arrange
        val identifikator = "123"
        val fagsakId = 1L

        val journalpost =
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker =
                    Bruker(
                        id = identifikator,
                        type = BrukerIdType.FNR,
                    ),
                kanal = "NAV_NO",
                dokumenter =
                    listOf(
                        DokumentInfo(
                            brevkode = "NAV 33-00.07",
                            tittel = "Søknad",
                            dokumentstatus = Dokumentstatus.FERDIGSTILT,
                            dokumentvarianter = emptyList(),
                            dokumentInfoId = "id",
                        ),
                    ),
            )

        every {
            mockedJournalpostBrukerService.tilPersonIdent(
                bruker = journalpost.bruker!!,
                tema = Tema.BAR,
            )
        } returns identifikator

        every { mockedBaSakClient.hentFagsaknummerPåPersonident(any()) } returns fagsakId

        every {
            mockedBaSakClient.hentMinimalRestFagsak(
                fagsakId = fagsakId,
            )
        } returns
            RestMinimalFagsak(
                id = fagsakId,
                behandlinger = listOf(),
                status = FagsakStatus.LØPENDE,
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpostBruker(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.BAR,
                behandlingstype = any(),
            )
        } returns
            Enhet(
                enhetId = "enhetId",
                enhetNavn = "enhetNavn",
            )

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isTrue()
    }

    @Test
    fun `skal ikke automatisk journalføre journalpost hvis søker er under 18 år`() {
        // Arrange
        val identifikator = "123"
        val journalpost = lagDigitalBarnetrygdsøknad(identifikator)

        mockSøkerUtenAdressebeskyttelse(journalpost, identifikator)

        every { mockedPdlClientService.hentPerson(identifikator, "hentperson-fødselsdato", Tema.BAR) } returns
            PdlPersonData(fødsel = listOf(Fødsel(LocalDate.now().minusYears(18).plusDays(1))))

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalpost hvis fødselsdato til søker er ukjent`() {
        // Arrange
        val identifikator = "123"
        val journalpost = lagDigitalBarnetrygdsøknad(identifikator)

        mockSøkerUtenAdressebeskyttelse(journalpost, identifikator)

        every { mockedPdlClientService.hentPerson(identifikator, "hentperson-fødselsdato", Tema.BAR) } returns
            PdlPersonData(fødsel = listOf(Fødsel(null)))

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal automatisk journalføre journalpost hvis søker fyller 18 år i dag`() {
        // Arrange
        val identifikator = "123"
        val fagsakId = 1L
        val journalpost = lagDigitalBarnetrygdsøknad(identifikator)

        mockSøkerUtenAdressebeskyttelse(journalpost, identifikator)

        every { mockedPdlClientService.hentPerson(identifikator, "hentperson-fødselsdato", Tema.BAR) } returns
            PdlPersonData(fødsel = listOf(Fødsel(LocalDate.now().minusYears(18))))

        every { mockedBaSakClient.hentFagsaknummerPåPersonident(identifikator) } returns fagsakId

        every { mockedBaSakClient.hentMinimalRestFagsak(fagsakId) } returns
            RestMinimalFagsak(
                id = fagsakId,
                behandlinger = listOf(),
                status = FagsakStatus.LØPENDE,
            )

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.BAR,
                behandlingstype = any(),
            )
        } returns
            Enhet(
                enhetId = "enhetId",
                enhetNavn = "enhetNavn",
            )

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isTrue()
    }

    private fun lagDigitalBarnetrygdsøknad(identifikator: String): Journalpost =
        Journalpost(
            journalpostId = "1",
            journalposttype = Journalposttype.I,
            journalstatus = Journalstatus.MOTTATT,
            bruker =
                Bruker(
                    id = identifikator,
                    type = BrukerIdType.FNR,
                ),
            kanal = "NAV_NO",
            dokumenter =
                listOf(
                    DokumentInfo(
                        brevkode = "NAV 33-00.07",
                        tittel = "Søknad",
                        dokumentstatus = Dokumentstatus.FERDIGSTILT,
                        dokumentvarianter = emptyList(),
                        dokumentInfoId = "id",
                    ),
                ),
        )

    private fun mockSøkerUtenAdressebeskyttelse(
        journalpost: Journalpost,
        identifikator: String,
    ) {
        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpostBruker(
                tema = Tema.BAR,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedJournalpostBrukerService.tilPersonIdent(
                bruker = journalpost.bruker!!,
                tema = Tema.BAR,
            )
        } returns identifikator
    }
}
