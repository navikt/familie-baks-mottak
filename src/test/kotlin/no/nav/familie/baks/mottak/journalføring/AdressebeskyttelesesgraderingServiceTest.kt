package no.nav.familie.baks.mottak.journalføring

class AdressebeskyttelesesgraderingServiceTest {
    /*

    TODO : Fix this test

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
                kanal = digitalKanal,
                dokumenter = hentDokumenterMedRiktigBrevkode(tema),
                bruker = Bruker("312", BrukerIdType.FNR),
            )

        // Act & Assert
        val exception =
            assertThrows<IllegalStateException> {
                enhetsnummerService.hentEnhetsnummer(journalpost)
            }

        assertThat(exception.message).isEqualTo("Støtter ikke tema $tema")
    }


     */
}
