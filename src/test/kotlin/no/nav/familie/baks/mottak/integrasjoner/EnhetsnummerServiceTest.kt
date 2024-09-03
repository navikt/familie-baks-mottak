package no.nav.familie.baks.mottak.integrasjoner

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.kontrakter.felles.Tema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class EnhetsnummerServiceTest {
    private val mockedHentEnhetClient: HentEnhetClient = mockk()
    private val mockedPdlClient: PdlClient = mockk()
    private val mockedSøknadFraJournalpostService: SøknadFraJournalpostService = mockk()
    private val arbeidsfordelingClient: ArbeidsfordelingClient = mockk()
    private val enhetsnummerService: EnhetsnummerService =
        EnhetsnummerService(
            hentEnhetClient = mockedHentEnhetClient,
            pdlClient = mockedPdlClient,
            søknadFraJournalpostService = mockedSøknadFraJournalpostService,
            arbeidsfordelingClient = arbeidsfordelingClient,
        )

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal sette enhet 4806 hvis enhet på journalpost er 2101 gitt at ingen personer har adressebeskyttelse strengt fortrolig`(tema: Tema) {
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
                kanal = "NAV_NO",
            )

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForBarnetrygd(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForKontantstøtte(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForBarnetrygd(journalpost.journalpostId)
        } returns emptyList()

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForKontantstøtte(journalpost.journalpostId)
        } returns emptyList()

        every {
            mockedPdlClient.hentPerson(fnr, "hentperson-med-adressebeskyttelse", tema)
        } returns PdlPersonData(adressebeskyttelse = listOf(Adressebeskyttelse(gradering = Adressebeskyttelsesgradering.UGRADERT)))

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
    fun `skal sette enhet 4817 hvis enhet på journalpost er 4847 gitt at ingen personer har adressebeskyttelse strengt fortrolig`(tema: Tema) {
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
                kanal = "NAV_NO",
            )

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForBarnetrygd(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForKontantstøtte(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForBarnetrygd(journalpost.journalpostId)
        } returns emptyList()

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForKontantstøtte(journalpost.journalpostId)
        } returns emptyList()

        every {
            mockedPdlClient.hentPerson(fnr, "hentperson-med-adressebeskyttelse", tema)
        } returns PdlPersonData(adressebeskyttelse = listOf(Adressebeskyttelse(gradering = Adressebeskyttelsesgradering.UGRADERT)))

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
    fun `skal sette enhet null hvis enhet på journalpost er null gitt at ingen personer har adressebeskyttelse strengt fortrolig`(tema: Tema) {
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
                kanal = "NAV_NO",
            )

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForBarnetrygd(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForKontantstøtte(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForBarnetrygd(journalpost.journalpostId)
        } returns emptyList()

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForKontantstøtte(journalpost.journalpostId)
        } returns emptyList()

        every {
            mockedPdlClient.hentPerson(fnr, "hentperson-med-adressebeskyttelse", tema)
        } returns PdlPersonData(adressebeskyttelse = listOf(Adressebeskyttelse(gradering = Adressebeskyttelsesgradering.UGRADERT)))

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
    fun `skal sette enhet fra journalpost hvis enhet kan behandle oppgaver gitt at ingen personer har adressebeskyttelse strengt fortrolig`(tema: Tema) {
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
                kanal = "NAV_NO",
            )

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForBarnetrygd(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForKontantstøtte(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForBarnetrygd(journalpost.journalpostId)
        } returns emptyList()

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForKontantstøtte(journalpost.journalpostId)
        } returns emptyList()

        every {
            mockedPdlClient.hentPerson(fnr, "hentperson-med-adressebeskyttelse", tema)
        } returns PdlPersonData(adressebeskyttelse = listOf(Adressebeskyttelse(gradering = Adressebeskyttelsesgradering.UGRADERT)))

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
    fun `skal sette enhet null hvis enhet ikke kan behandle oppgaver gitt at ingen personer har adressebeskyttelse strengt fortrolig`(tema: Tema) {
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
                kanal = "NAV_NO",
            )

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForBarnetrygd(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForKontantstøtte(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForBarnetrygd(journalpost.journalpostId)
        } returns emptyList()

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForKontantstøtte(journalpost.journalpostId)
        } returns emptyList()

        every {
            mockedPdlClient.hentPerson(fnr, "hentperson-med-adressebeskyttelse", tema)
        } returns PdlPersonData(adressebeskyttelse = listOf(Adressebeskyttelse(gradering = Adressebeskyttelsesgradering.UGRADERT)))

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
    fun `skal sette enhet null hvis enhet er nedlagt gitt at ingen personer har adressebeskyttelse strengt fortrolig`(tema: Tema) {
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
                kanal = "NAV_NO",
            )

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForBarnetrygd(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForKontantstøtte(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForBarnetrygd(journalpost.journalpostId)
        } returns emptyList()

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForKontantstøtte(journalpost.journalpostId)
        } returns emptyList()

        every {
            mockedPdlClient.hentPerson(fnr, "hentperson-med-adressebeskyttelse", tema)
        } returns PdlPersonData(adressebeskyttelse = listOf(Adressebeskyttelse(gradering = Adressebeskyttelsesgradering.UGRADERT)))

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
    fun `skal sette enhet til 2103 Vikafossen hvis søker har adressebeskyttelse strengt fortrolig`(tema: Tema) {
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
                kanal = "NAV_NO",
            )

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForBarnetrygd(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentSøkersIdentForKontantstøtte(journalpost.journalpostId)
        } returns fnr

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForBarnetrygd(journalpost.journalpostId)
        } returns listOf("123", "456")

        every {
            mockedSøknadFraJournalpostService.hentBarnasIdenterForKontantstøtte(journalpost.journalpostId)
        } returns listOf("123", "456")

        every {
            mockedPdlClient.hentPerson(fnr, "hentperson-med-adressebeskyttelse", tema)
        } returns PdlPersonData(adressebeskyttelse = listOf(Adressebeskyttelse(gradering = Adressebeskyttelsesgradering.UGRADERT)))

        every {
            mockedPdlClient.hentPerson("123", graphqlfil = "hentperson-med-adressebeskyttelse", tema = tema)
        } returns PdlPersonData(adressebeskyttelse = listOf(Adressebeskyttelse(gradering = Adressebeskyttelsesgradering.STRENGT_FORTROLIG)))

        every {
            mockedPdlClient.hentPerson("456", graphqlfil = "hentperson-med-adressebeskyttelse", tema = tema)
        } returns PdlPersonData(adressebeskyttelse = listOf(Adressebeskyttelse(gradering = Adressebeskyttelsesgradering.UGRADERT)))

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
                kanal = "NAV_NO",
            )

        // Act & Assert
        val exception =
            assertThrows<IllegalStateException> {
                enhetsnummerService.hentEnhetsnummer(journalpost)
            }

        assertThat(exception.message).isEqualTo("Tema er null")
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `skal kaste feil når tema ikke er støttet`(tema: Tema) {
        // Arrange
        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = tema.name,
                journalforendeEnhet = "1",
                kanal = "NAV_NO",
            )

        // Act & Assert
        val exception =
            assertThrows<IllegalStateException> {
                enhetsnummerService.hentEnhetsnummer(journalpost)
            }

        assertThat(exception.message).isEqualTo("Fant ikke bruker på journalpost ved forsøk på henting av behandlende enhet")
    }
}
