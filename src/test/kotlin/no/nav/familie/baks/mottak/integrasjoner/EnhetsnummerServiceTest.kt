package no.nav.familie.baks.mottak.integrasjoner

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EnhetsnummerServiceTest {
    private val mockedHentEnhetClient: HentEnhetClient = mockk()
    private val mockedPdlClient: PdlClient = mockk()
    private val mockedSøknadFraJournalpostService: SøknadFraJournalpostService = mockk()
    private val enhetsnummerService: EnhetsnummerService = EnhetsnummerService(
        hentEnhetClient = mockedHentEnhetClient,
        pdlClient = mockedPdlClient,
        søknadFraJournalpostService = mockedSøknadFraJournalpostService
    )

    @Test
    fun `skal sette enhet 4806 hvis enhet på journalpost er 2101 gitt at ingen personer har adressebeskyttelse strengt fortrolig`() {
        // Arrange
        val journalpost = Journalpost(
            journalpostId = "123",
            journalposttype = Journalposttype.I,
            journalstatus = Journalstatus.MOTTATT,
            tema = Tema.BAR.name,
            journalforendeEnhet = "2101"
        );

        every {
            mockedSøknadFraJournalpostService.hentIdenterForBarnetrygd(journalpost.journalpostId)
        } returns emptyList()

        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isEqualTo("4806")
    }

    @Test
    fun `skal sette enhet null hvis enhet på journalpost er null gitt at ingen personer har adressebeskyttelse strengt fortrolig`() {
        // Arrange
        val journalpost = Journalpost(
            journalpostId = "123",
            journalposttype = Journalposttype.I,
            journalstatus = Journalstatus.MOTTATT,
            tema = Tema.BAR.name,
            journalforendeEnhet = null
        );

        every {
            mockedSøknadFraJournalpostService.hentIdenterForBarnetrygd(journalpost.journalpostId)
        } returns emptyList()

        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isNull()
    }

    @Test
    fun `skal sette enhet fra journalpost hvis enhet kan behandle oppgaver gitt at ingen personer har adressebeskyttelse strengt fortrolig`() {
        // Arrange
        val journalpost = Journalpost(
            journalpostId = "123",
            journalposttype = Journalposttype.I,
            journalstatus = Journalstatus.MOTTATT,
            tema = Tema.BAR.name,
            journalforendeEnhet = "4"
        );

        every {
            mockedSøknadFraJournalpostService.hentIdenterForBarnetrygd(journalpost.journalpostId)
        } returns emptyList()

        every { mockedHentEnhetClient.hentEnhet("4") } returns Enhet("4", "enhetnavn", true, "Aktiv")

        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isEqualTo("4")
    }

    @Test
    fun `skal sette enhet null hvis enhet ikke kan behandle oppgaver gitt at ingen personer har adressebeskyttelse strengt fortrolig`() {
        // Arrange
        val journalpost = Journalpost(
            journalpostId = "123",
            journalposttype = Journalposttype.I,
            journalstatus = Journalstatus.MOTTATT,
            tema = Tema.BAR.name,
            journalforendeEnhet = "4"
        );

        every {
            mockedSøknadFraJournalpostService.hentIdenterForBarnetrygd(journalpost.journalpostId)
        } returns emptyList()

        every { mockedHentEnhetClient.hentEnhet("4") } returns Enhet("4", "enhetnavn", false, "Aktiv")

        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isNull()
    }

    @Test
    fun `skal sette enhet null hvis enhet er nedlagt gitt at ingen personer har adressebeskyttelse strengt fortrolig`() {
        // Arrange
        val journalpost = Journalpost(
            journalpostId = "123",
            journalposttype = Journalposttype.I,
            journalstatus = Journalstatus.MOTTATT,
            tema = Tema.BAR.name,
            journalforendeEnhet = "4"
        );

        every {
            mockedSøknadFraJournalpostService.hentIdenterForBarnetrygd(journalpost.journalpostId)
        } returns emptyList()

        every { mockedHentEnhetClient.hentEnhet("4") } returns Enhet("4", "enhetnavn", true, "Nedlagt")

        // Act
        val enhetsnummer = enhetsnummerService.hentEnhetsnummer(journalpost)

        // Assert
        assertThat(enhetsnummer).isNull()
    }
}
