package no.nav.familie.baks.mottak.journalføring

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.integrasjoner.ArbeidsfordelingClient
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.baks.mottak.integrasjoner.BehandlingStatus
import no.nav.familie.baks.mottak.integrasjoner.BehandlingType
import no.nav.familie.baks.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
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
import no.nav.familie.unleash.UnleashService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AutomatiskJournalføringBarnetrygdServiceTest {
    private val mockedUnleashService: UnleashService = mockk()
    private val mockedBaSakClient: BaSakClient = mockk()
    private val mockedArbeidsfordelingClient: ArbeidsfordelingClient = mockk()
    private val mockedAdressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService = mockk()
    private val mockedJournalpostBrukerService: JournalpostBrukerService = mockk()
    private val automatiskJournalføringBarnetrygdService: AutomatiskJournalføringBarnetrygdService =
        AutomatiskJournalføringBarnetrygdService(
            unleashService = mockedUnleashService,
            baSakClient = mockedBaSakClient,
            arbeidsfordelingClient = mockedArbeidsfordelingClient,
            adressebeskyttelesesgraderingService = mockedAdressebeskyttelesesgraderingService,
            journalpostBrukerService = mockedJournalpostBrukerService,
        )

    @Test
    fun `skal ikke automatisk journalføre journalpost om toggle er av`() {
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
            mockedUnleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER,
                defaultValue = false,
            )
        } returns false

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
                false,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
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

        every {
            mockedUnleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER,
                defaultValue = false,
            )
        } returns true

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
                false,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalpost om bruker har fagsak i infotrygd`() {
        // Arrange
        val journalpost =
            Journalpost(
                journalpostId = "1",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
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
            mockedUnleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER,
                defaultValue = false,
            )
        } returns true

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
                true,
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

        every {
            mockedUnleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER,
                defaultValue = false,
            )
        } returns true

        // Act
        val skalAutomatiskJournalføres =
            automatiskJournalføringBarnetrygdService.skalAutomatiskJournalføres(
                journalpost,
                false,
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
            mockedUnleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER,
                defaultValue = false,
            )
        } returns true

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
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.BAR,
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
                false,
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
            mockedUnleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER,
                defaultValue = false,
            )
        } returns true

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
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.BAR,
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
                false,
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
            mockedUnleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER,
                defaultValue = false,
            )
        } returns true

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
                false,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
    }

    @Test
    fun `skal ikke automatisk journalføre journalpost hvis det finnes en tilknyttet strengt fortrolig person`() {
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
            mockedUnleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER,
                defaultValue = false,
            )
        } returns true

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
                false,
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
            mockedUnleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER,
                defaultValue = false,
            )
        } returns true

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
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.BAR,
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
                false,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isFalse()
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
            mockedUnleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER,
                defaultValue = false,
            )
        } returns true

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
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(
                personIdent = identifikator,
                tema = Tema.BAR,
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
                false,
            )

        // Assert
        assertThat(skalAutomatiskJournalføres).isTrue()
    }
}
