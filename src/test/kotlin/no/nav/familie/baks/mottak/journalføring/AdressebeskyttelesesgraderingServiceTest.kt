package no.nav.familie.baks.mottak.journalføring

import io.mockk.mockk
import no.nav.familie.baks.mottak.integrasjoner.Bruker
import no.nav.familie.baks.mottak.integrasjoner.BrukerIdType
import no.nav.familie.baks.mottak.integrasjoner.DokumentInfo
import no.nav.familie.baks.mottak.integrasjoner.Dokumentstatus
import no.nav.familie.baks.mottak.integrasjoner.Journalpost
import no.nav.familie.baks.mottak.integrasjoner.Journalposttype
import no.nav.familie.baks.mottak.integrasjoner.Journalstatus
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.SøknadsidenterService
import no.nav.familie.kontrakter.felles.Tema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class AdressebeskyttelesesgraderingServiceTest {
    private val mockedPdlClient: PdlClient = mockk()
    private val mockedSøknadsidenterService: SøknadsidenterService = mockk()
    private val mockedJournalpostBrukerService: JournalpostBrukerService = mockk()

    private val adressebeskyttelesesgraderingService =
        AdressebeskyttelesesgraderingService(
            pdlClient = mockedPdlClient,
            søknadsidenterService = mockedSøknadsidenterService,
            journalpostBrukerService = mockedJournalpostBrukerService,
        )

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `skal kaste feil når tema ikke er støttet`(
        tema: Tema,
    ) {
        // Arrange
        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = tema.name,
                journalforendeEnhet = "1",
                kanal = "NAV_NO",
                dokumenter = hentDokumenterMedRiktigBrevkode(tema),
                bruker = Bruker("312", BrukerIdType.FNR),
            )

        // Act & Assert
        val exception =
            assertThrows<IllegalStateException> {
                adressebeskyttelesesgraderingService.finnesAdressebeskyttelsegradringPåJournalpost(tema, journalpost)
            }

        assertThat(exception.message).isEqualTo("Støtter ikke tema $tema")
    }

    @Test
    fun `skal kaste feil når bruker ikke er satt på journalpost`() {
        // Arrange
        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = Tema.BAR.name,
                journalforendeEnhet = "1",
                kanal = "NAV_NO",
                dokumenter = hentDokumenterMedRiktigBrevkode(Tema.BAR),
                bruker = null,
            )

        // Act & Assert
        val exception =
            assertThrows<IllegalStateException> {
                adressebeskyttelesesgraderingService.finnesAdressebeskyttelsegradringPåJournalpost(Tema.BAR, journalpost)
            }

        assertThat(exception.message).isEqualTo("Bruker på journalpost ${journalpost.journalpostId} kan ikke være null")
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `asd`(
        tema: Tema,
    ) {
        // Arrange
        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = tema.name,
                journalforendeEnhet = "1",
                kanal = "NAV_NO",
                dokumenter = hentDokumenterMedRiktigBrevkode(tema),
                bruker = Bruker("312", BrukerIdType.FNR),
            )

        // Act & Assert
        val exception =
            assertThrows<IllegalStateException> {
                adressebeskyttelesesgraderingService.finnesAdressebeskyttelsegradringPåJournalpost(tema, journalpost)
            }

        assertThat(exception.message).isEqualTo("Støtter ikke tema $tema")
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
