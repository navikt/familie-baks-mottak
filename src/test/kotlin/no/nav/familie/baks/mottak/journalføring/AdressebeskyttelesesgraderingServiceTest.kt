package no.nav.familie.baks.mottak.journalføring

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.integrasjoner.Adressebeskyttelse
import no.nav.familie.baks.mottak.integrasjoner.Adressebeskyttelsesgradering
import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.baks.mottak.integrasjoner.PdlNotFoundException
import no.nav.familie.baks.mottak.integrasjoner.PdlPersonData
import no.nav.familie.baks.mottak.integrasjoner.SøknadsidenterService
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.DokumentInfo
import no.nav.familie.kontrakter.felles.journalpost.Dokumentstatus
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class AdressebeskyttelesesgraderingServiceTest {
    private val mockedPdlClient: PdlClientService = mockk()
    private val mockedSøknadsidenterService: SøknadsidenterService = mockk()
    private val mockedJournalpostBrukerService: JournalpostBrukerService = mockk()

    private val adressebeskyttelesesgraderingService =
        AdressebeskyttelesesgraderingService(
            pdlClientService = mockedPdlClient,
            søknadsidenterService = mockedSøknadsidenterService,
            journalpostBrukerService = mockedJournalpostBrukerService,
        )

    @Test
    fun `skal kaste feil når bruker ikke er satt på journalpost`() {
        // Arrange
        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker = null,
            )

        // Act & Assert
        val exception =
            assertThrows<IllegalStateException> {
                adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                    tema = Tema.BAR,
                    journalpost = journalpost,
                )
            }
        assertThat(exception.message).isEqualTo("Bruker på journalpost ${journalpost.journalpostId} kan ikke være null")
    }

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
                bruker = Bruker("312", BrukerIdType.FNR),
            )

        // Act & Assert
        val exception =
            assertThrows<IllegalStateException> {
                adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                    tema = tema,
                    journalpost = journalpost,
                )
            }
        assertThat(exception.message).isEqualTo("Støtter ikke tema $tema")
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal returnere true om journalpost fra digital søknad er tilknyttet en strengt fotrolig person`(
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

        every {
            mockedSøknadsidenterService.hentIdenterFraKontantstøtteSøknad(
                journalpostId = journalpost.journalpostId,
            )
        } returns listOf("123456789")

        every {
            mockedSøknadsidenterService.hentIdenterFraBarnetrygdSøknad(
                journalpostId = journalpost.journalpostId,
            )
        } returns listOf("123456789")

        every {
            mockedPdlClient.hentPerson(
                personIdent = "123456789",
                graphqlfil = "hentperson-med-adressebeskyttelse",
                tema = tema,
            )
        } returns
            PdlPersonData(
                adressebeskyttelse =
                    listOf(
                        Adressebeskyttelse(
                            gradering = Adressebeskyttelsesgradering.STRENGT_FORTROLIG,
                        ),
                    ),
            )

        // Act
        val finnesAdressebeskyttelsegradringPåJournalpost =
            adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = tema,
                journalpost = journalpost,
            )

        // Assert
        assertThat(finnesAdressebeskyttelsegradringPåJournalpost).isTrue()
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal returnere true om journalpost fra papirsøknad er tilknyttet en strengt fotrolig person`(
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
                kanal = "IKKE_NAV_NO",
                dokumenter = hentDokumenterMedRiktigBrevkode(tema),
                bruker = Bruker("312", BrukerIdType.FNR),
            )

        every {
            mockedJournalpostBrukerService.tilPersonIdent(
                bruker = journalpost.bruker!!,
                tema = tema,
            )
        } returns "123456789"

        every {
            mockedPdlClient.hentPerson(
                personIdent = "123456789",
                graphqlfil = "hentperson-med-adressebeskyttelse",
                tema = tema,
            )
        } returns
            PdlPersonData(
                adressebeskyttelse =
                    listOf(
                        Adressebeskyttelse(
                            gradering = Adressebeskyttelsesgradering.STRENGT_FORTROLIG,
                        ),
                    ),
            )

        // Act
        val finnesAdressebeskyttelsegradringPåJournalpost =
            adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = tema,
                journalpost = journalpost,
            )

        // Assert
        assertThat(finnesAdressebeskyttelsegradringPåJournalpost).isTrue()
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal returnere false om journalpost fra digital søknad ikke er tilknyttet en strengt fotrolig person`(
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

        every {
            mockedSøknadsidenterService.hentIdenterFraKontantstøtteSøknad(
                journalpostId = journalpost.journalpostId,
            )
        } returns listOf("123456789")

        every {
            mockedSøknadsidenterService.hentIdenterFraBarnetrygdSøknad(
                journalpostId = journalpost.journalpostId,
            )
        } returns listOf("123456789")

        every {
            mockedPdlClient.hentPerson(
                personIdent = "123456789",
                graphqlfil = "hentperson-med-adressebeskyttelse",
                tema = tema,
            )
        } returns
            PdlPersonData(
                adressebeskyttelse =
                    listOf(
                        Adressebeskyttelse(
                            gradering = Adressebeskyttelsesgradering.UGRADERT,
                        ),
                    ),
            )

        // Act
        val finnesAdressebeskyttelsegradringPåJournalpost =
            adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = tema,
                journalpost = journalpost,
            )

        // Assert
        assertThat(finnesAdressebeskyttelsegradringPåJournalpost).isFalse()
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal returnere false om journalpost fra papirsøknad ikke er tilknyttet en strengt fotrolig person`(
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
                kanal = "IKKE_NAV_NO",
                dokumenter = hentDokumenterMedRiktigBrevkode(tema),
                bruker = Bruker("312", BrukerIdType.FNR),
            )

        every {
            mockedJournalpostBrukerService.tilPersonIdent(
                bruker = journalpost.bruker!!,
                tema = tema,
            )
        } returns "123456789"

        every {
            mockedPdlClient.hentPerson(
                personIdent = "123456789",
                graphqlfil = "hentperson-med-adressebeskyttelse",
                tema = tema,
            )
        } returns
            PdlPersonData(
                adressebeskyttelse =
                    listOf(
                        Adressebeskyttelse(
                            gradering = Adressebeskyttelsesgradering.UGRADERT,
                        ),
                    ),
            )

        // Act
        val finnesAdressebeskyttelsegradringPåJournalpost =
            adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = tema,
                journalpost = journalpost,
            )

        // Assert
        assertThat(finnesAdressebeskyttelsegradringPåJournalpost).isFalse()
    }

    private fun hentDokumenterMedRiktigBrevkode(tema: Tema): List<DokumentInfo> {
        val brevkode =
            when (tema) {
                Tema.BAR -> "NAV 33-00.07"
                Tema.KON -> "NAV 34-00.08"
                else -> ""
            }
        return listOf(DokumentInfo(brevkode = brevkode, tittel = "Søknad", dokumentstatus = Dokumentstatus.FERDIGSTILT, dokumentvarianter = emptyList(), dokumentInfoId = ""))
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal returnere false dersom ident i journalpost ikke er knyttet til en person i PDL`(tema: Tema) {
        // Arrange
        val journalpost =
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                tema = tema.name,
                journalforendeEnhet = "1",
                kanal = "NAV_NO",
                dokumenter = emptyList(),
                bruker = Bruker("312", BrukerIdType.FNR),
            )

        val ident = journalpost.bruker!!.id

        every { mockedJournalpostBrukerService.tilPersonIdent(journalpost.bruker!!, tema) } returns ident
        every { mockedPdlClient.hentPerson(ident, "hentperson-med-adressebeskyttelse", tema) } throws PdlNotFoundException("Fant ingen person for ident", mockk(), ident)
        // Act
        val finnesAdressebeskyttelsegradringPåJournalpost =
            adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = tema,
                journalpost = journalpost,
            )

        // Assert
        assertThat(finnesAdressebeskyttelsegradringPåJournalpost).isFalse()
    }

    @ParameterizedTest
    @EnumSource(
        value = Tema::class,
        names = ["BAR", "KON"],
    )
    fun `skal returnere true dersom en av personene har adressebeskyttelse selv om en av identene ikke tilhører en person`(tema: Tema) {
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

        val ident = journalpost.bruker!!.id

        val ikkeEksisterendeIdent = "ikke-eksisterende-ident"

        every { mockedSøknadsidenterService.hentIdenterFraBarnetrygdSøknad(journalpost.journalpostId) } returns listOf(ident, ikkeEksisterendeIdent)
        every { mockedSøknadsidenterService.hentIdenterFraKontantstøtteSøknad(journalpost.journalpostId) } returns listOf(ident, ikkeEksisterendeIdent)
        every { mockedPdlClient.hentPerson(ikkeEksisterendeIdent, "hentperson-med-adressebeskyttelse", tema) } throws PdlNotFoundException("Fant ingen person for ident", mockk(), ident)

        every { mockedPdlClient.hentPerson(ident, "hentperson-med-adressebeskyttelse", tema) } returns
            PdlPersonData(
                adressebeskyttelse =
                    listOf(
                        Adressebeskyttelse(
                            gradering = Adressebeskyttelsesgradering.STRENGT_FORTROLIG,
                        ),
                    ),
            )
        // Act
        val finnesAdressebeskyttelsegradringPåJournalpost =
            adressebeskyttelesesgraderingService.finnesStrengtFortroligAdressebeskyttelsegraderingPåJournalpost(
                tema = tema,
                journalpost = journalpost,
            )

        // Assert
        assertThat(finnesAdressebeskyttelsegradringPåJournalpost).isTrue
    }
}
