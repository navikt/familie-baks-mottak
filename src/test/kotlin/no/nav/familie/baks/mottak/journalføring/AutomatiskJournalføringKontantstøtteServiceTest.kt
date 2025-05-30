package no.nav.familie.baks.mottak.journalføring

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.ArbeidsfordelingClient
import no.nav.familie.baks.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.baks.mottak.integrasjoner.BehandlingStatus
import no.nav.familie.baks.mottak.integrasjoner.BehandlingType
import no.nav.familie.baks.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AutomatiskJournalføringKontantstøtteServiceTest {
    private val mockedUnleashService: UnleashNextMedContextService = mockk()
    private val mockedArbeidsfordelingClient: ArbeidsfordelingClient = mockk()
    private val mockedKsSakClient: KsSakClient = mockk()
    private val mockedAdressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService = mockk()
    private val mockedJournalpostBrukerService: JournalpostBrukerService = mockk()

    private val automatiskJournalføringKontantstøtteService: AutomatiskJournalføringKontantstøtteService =
        AutomatiskJournalføringKontantstøtteService(
            unleashService = mockedUnleashService,
            arbeidsfordelingClient = mockedArbeidsfordelingClient,
            ksSakClient = mockedKsSakClient,
            adressebeskyttelesesgraderingService = mockedAdressebeskyttelesesgraderingService,
            journalpostBrukerService = mockedJournalpostBrukerService,
        )

    @Test
    fun `skal automatisk journalføre journalposten`() {
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
                            brevkode = "NAV 34-00.08",
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
                tema = Tema.KON,
            )
        } returns identifikator

        every { mockedKsSakClient.hentFagsaknummerPåPersonident(any()) } returns fagsakId

        every {
            mockedKsSakClient.hentMinimalRestFagsak(
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
                tema = Tema.KON,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.KON,
            )
        } returns
            Enhet(
                enhetId = "enhetId",
                enhetNavn = "enhetNavn",
            )

        // Act
        val skalAutomatiskJournalføres = automatiskJournalføringKontantstøtteService.skalAutomatiskJournalføres(journalpost)

        // Assert
        assertThat(skalAutomatiskJournalføres).isTrue()
    }

    @Test
    fun `skal ikke automatisk journalføre journalposten hvis det er orgnummer`() {
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
                            brevkode = "NAV 34-00.08",
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
                tema = Tema.KON,
            )
        } returns identifikator

        every { mockedKsSakClient.hentFagsaknummerPåPersonident(any()) } returns fagsakId

        every {
            mockedKsSakClient.hentMinimalRestFagsak(
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
                tema = Tema.KON,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.KON,
            )
        } returns
            Enhet(
                enhetId = "enhetId",
                enhetNavn = "enhetNavn",
            )

        // Act
        val skalAutomatiskJournalføres = automatiskJournalføringKontantstøtteService.skalAutomatiskJournalføres(journalpost)

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalposten om journalposten ikke er kontanstøtte søknad`() {
        // Arrange
        val identifikator = "123"

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
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.KON,
                journalpost = journalpost,
            )
        } returns false

        // Act
        val skalAutomatiskJournalføres = automatiskJournalføringKontantstøtteService.skalAutomatiskJournalføres(journalpost)

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalposten hvis søknad ikke er sendt inn digitalt`() {
        // Arrange
        val identifikator = "123"

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
                kanal = "SKAN_NETS",
                dokumenter =
                    listOf(
                        DokumentInfo(
                            brevkode = "NAV 34-00.08",
                            tittel = "Søknad",
                            dokumentstatus = Dokumentstatus.FERDIGSTILT,
                            dokumentvarianter = emptyList(),
                            dokumentInfoId = "id",
                        ),
                    ),
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.KON,
                journalpost = journalpost,
            )
        } returns false

        // Act
        val skalAutomatiskJournalføres = automatiskJournalføringKontantstøtteService.skalAutomatiskJournalføres(journalpost)

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalposten hvis geografisk behandlende enhet er i listen over eksluderte enheter`() {
        // Arrange
        val identifikator = "123"

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
                            brevkode = "NAV 34-00.08",
                            tittel = "Søknad",
                            dokumentstatus = Dokumentstatus.FERDIGSTILT,
                            dokumentvarianter = emptyList(),
                            dokumentInfoId = "id",
                        ),
                    ),
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.KON,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.KON,
            )
        } returns
            Enhet(
                enhetId = "4863",
                enhetNavn = "midlertidigEnhet",
            )

        every {
            mockedJournalpostBrukerService.tilPersonIdent(
                bruker = journalpost.bruker!!,
                tema = Tema.KON,
            )
        } returns identifikator

        // Act
        val skalAutomatiskJournalføres = automatiskJournalføringKontantstøtteService.skalAutomatiskJournalføres(journalpost)

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalposten hvis en av personene i søknaden har adressebeskyttelsegradering strengt fortrolig`() {
        // Arrange
        val identifikator = "123"

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
                            brevkode = "NAV 34-00.08",
                            tittel = "Søknad",
                            dokumentstatus = Dokumentstatus.FERDIGSTILT,
                            dokumentvarianter = emptyList(),
                            dokumentInfoId = "id",
                        ),
                    ),
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = Tema.KON,
                journalpost = journalpost,
            )
        } returns true

        // Act
        val skalAutomatiskJournalføres = automatiskJournalføringKontantstøtteService.skalAutomatiskJournalføres(journalpost)

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalposten om det finnes en åpen behandling på fagsak`() {
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
                            brevkode = "NAV 34-00.08",
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
                tema = Tema.KON,
            )
        } returns identifikator

        every { mockedKsSakClient.hentFagsaknummerPåPersonident(any()) } returns fagsakId

        every {
            mockedKsSakClient.hentMinimalRestFagsak(
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
                tema = Tema.KON,
                journalpost = journalpost,
            )
        } returns false

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.KON,
            )
        } returns
            Enhet(
                enhetId = "enhetId",
                enhetNavn = "enhetNavn",
            )

        // Act
        val skalAutomatiskJournalføres = automatiskJournalføringKontantstøtteService.skalAutomatiskJournalføres(journalpost)

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }
}
