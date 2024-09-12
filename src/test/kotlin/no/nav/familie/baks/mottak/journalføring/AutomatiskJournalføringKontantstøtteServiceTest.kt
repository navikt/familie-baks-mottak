package no.nav.familie.baks.mottak.journalføring

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.*
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.arbeidsfordeling.Enhet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
                        ),
                    ),
            )

        every {
            mockedUnleashService.isEnabled(
                toggleId = FeatureToggleConfig.AUTOMATISK_JOURNALFØRING_AV_KONTANTSTØTTE_SØKNADER,
                defaultValue = false,
            )
        } returns true

        every {
            mockedJournalpostBrukerService.tilPersonIdent(
                bruker = journalpost.bruker!!,
                tema = Tema.KON,
            )
        } returns identifikator

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
            mockedAdressebeskyttelesesgraderingService.finnesAdressebeskyttelsegradringPåJournalpost(
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
        val skalAutomatiskJournalføres = automatiskJournalføringKontantstøtteService.skalAutomatiskJournalføres(journalpost, false, fagsakId)

        // Assert
        assertThat(skalAutomatiskJournalføres).isTrue()
    }
}
