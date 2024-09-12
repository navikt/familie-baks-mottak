package no.nav.familie.baks.mottak.integrasjoner

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.journalføring.AdressebeskyttelesesgraderingService
import no.nav.familie.baks.mottak.journalføring.JournalpostBrukerService
import no.nav.familie.kontrakter.felles.Tema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class EnhetsnummerServiceTest {
    private val mockedHentEnhetClient: HentEnhetClient = mockk()
    private val mockedArbeidsfordelingClient: ArbeidsfordelingClient = mockk()
    private val mockedAdressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService = mockk()
    private val mockedJournalpostBrukerService: JournalpostBrukerService = mockk()
    private val enhetsnummerService: EnhetsnummerService =
        EnhetsnummerService(
            hentEnhetClient = mockedHentEnhetClient,
            arbeidsfordelingClient = mockedArbeidsfordelingClient,
            adressebeskyttelesesgraderingService = mockedAdressebeskyttelesesgraderingService,
            journalpostBrukerService = mockedJournalpostBrukerService,
        )

    private val digitalKanal = "NAV_NO"

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal sette enhet 4806 hvis enhet på journalpost er 2101 gitt at ingen personer har adressebeskyttelse strengt fortrolig`(
        tema: Tema,
    ) {
        // Arrange
        val fnr = "321"

        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = tema.name,
                journalforendeEnhet = "2101",
                bruker = Bruker(fnr, BrukerIdType.FNR),
                kanal = digitalKanal,
                dokumenter = hentDokumenterMedRiktigBrevkode(tema),
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)
        } returns false

        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isEqualTo("4806")
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal sette enhet 4817 hvis enhet på journalpost er 4847 gitt at ingen personer har adressebeskyttelse strengt fortrolig`(
        tema: Tema,
    ) {
        // Arrange
        val fnr = "321"

        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = tema.name,
                journalforendeEnhet = "4847",
                bruker = Bruker(fnr, BrukerIdType.FNR),
                kanal = digitalKanal,
                dokumenter = hentDokumenterMedRiktigBrevkode(tema),
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)
        } returns false

        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isEqualTo("4817")
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal sette enhet null hvis enhet på journalpost er null gitt at ingen personer har adressebeskyttelse strengt fortrolig`(
        tema: Tema,
    ) {
        // Arrange
        val fnr = "321"

        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = tema.name,
                journalforendeEnhet = null,
                bruker = Bruker(fnr, BrukerIdType.FNR),
                kanal = digitalKanal,
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)
        } returns false

        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isNull()
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal sette enhet fra journalpost hvis enhet kan behandle oppgaver gitt at ingen personer har adressebeskyttelse strengt fortrolig`(
        tema: Tema,
    ) {
        // Arrange
        val fnr = "321"

        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = tema.name,
                journalforendeEnhet = "1234",
                bruker = Bruker(fnr, BrukerIdType.FNR),
                kanal = digitalKanal,
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)
        } returns false

        every {
            mockedHentEnhetClient.hentEnhet("1234")
        } returns Enhet("1234", "enhetnavn", true, "Aktiv")

        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isEqualTo("1234")
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal sette enhet null hvis enhet ikke kan behandle oppgaver gitt at ingen personer har adressebeskyttelse strengt fortrolig`(
        tema: Tema,
    ) {
        // Arrange
        val fnr = "321"

        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = tema.name,
                journalforendeEnhet = "1234",
                bruker = Bruker(fnr, BrukerIdType.FNR),
                kanal = digitalKanal,
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)
        } returns false

        every {
            mockedHentEnhetClient.hentEnhet("1234")
        } returns Enhet("1234", "enhetnavn", false, "Aktiv")

        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isNull()
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal sette enhet null hvis enhet er nedlagt gitt at ingen personer har adressebeskyttelse strengt fortrolig`(
        tema: Tema,
    ) {
        // Arrange
        val fnr = "321"

        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = tema.name,
                journalforendeEnhet = "1234",
                bruker = Bruker(fnr, BrukerIdType.FNR),
                kanal = digitalKanal,
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)
        } returns false

        every {
            mockedHentEnhetClient.hentEnhet("1234")
        } returns Enhet("1234", "enhetnavn", true, "Nedlagt")

        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isNull()
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal sette enhet til 2103 Vikafossen hvis søker har adressebeskyttelse strengt fortrolig`(
        tema: Tema,
    ) {
        // Arrange
        val fnr = "321"

        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = tema.name,
                journalforendeEnhet = "1234",
                bruker = Bruker(fnr, BrukerIdType.FNR),
                kanal = digitalKanal,
                dokumenter = hentDokumenterMedRiktigBrevkode(tema),
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)
        } returns true

        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isEqualTo("2103")
    }

    @Test
    fun `skal throwe feil hvis tema ikke er satt`() {
        // Arrange
        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = null,
                journalforendeEnhet = "1",
                kanal = digitalKanal,
                dokumenter = hentDokumenterMedRiktigBrevkode(Tema.BAR),
            )

        // Act & Assert
        val exception =
            assertThrows<IllegalStateException> {
                enhetsnummerService.hentEnhetsnummer(journalpost)
            }

        assertThat(exception.message).isEqualTo("Tema er null")
    }

    @Test
    fun `skal kaste exception hvis journalpost bruker er null`() {
        // Arrange
        val journalpostId = "123"

        val journalpost =
            Journalpost(
                journalpostId = journalpostId,
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = Tema.BAR.name,
                journalforendeEnhet = "1",
                kanal = digitalKanal,
                dokumenter = hentDokumenterMedRiktigBrevkode(Tema.BAR),
                bruker = null,
            )

        // Act & Assert
        val exception =
            assertThrows<IllegalStateException> {
                enhetsnummerService.hentEnhetsnummer(journalpost)
            }

        assertThat(exception.message).isEqualTo("Bruker for journalpost $journalpostId er null. Usikker på hvordan dette burde håndteres.")
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal finne og sette geografisk behandlende enhet på digitale søknader dersom ingen adressebeskyttelse er nødvendig`(
        tema: Tema,
    ) {
        // Arrange
        val fnr = "321"

        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = tema.name,
                journalforendeEnhet = "1234",
                bruker = Bruker(fnr, BrukerIdType.FNR),
                kanal = digitalKanal,
                dokumenter = hentDokumenterMedRiktigBrevkode(tema),
            )

        every {
            mockedAdressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(tema, journalpost)
        } returns false

        every {
            mockedJournalpostBrukerService.tilPersonIdent(journalpost.bruker!!, tema)
        } returns "123456789"

        every {
            mockedArbeidsfordelingClient.hentBehandlendeEnhetPåIdent(any(), any())
        } returns
            no.nav.familie.kontrakter.felles.arbeidsfordeling
                .Enhet(enhetId = "789", "Hønefoss")
        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isEqualTo("789")
    }

    private fun hentDokumenterMedRiktigBrevkode(tema: Tema): List<DokumentInfo> {
        val brevkode =
            when (tema) {
                Tema.BAR -> "NAV 33-00.07"
                Tema.KON -> "NAV 34-00.08"
                else -> ""
            }
        return listOf(DokumentInfo(brevkode = brevkode, tittel = "Søknad", dokumentstatus = Dokumentstatus.FERDIGSTILT, dokumentvarianter = emptyList()))
    }
}
