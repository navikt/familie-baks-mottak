package no.nav.familie.baks.mottak.journalføring

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.integrasjoner.Bruker
import no.nav.familie.baks.mottak.integrasjoner.BrukerIdType
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.felles.Tema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JournalpostBrukerServiceTest {
    private val mockedPdlClient: PdlClient = mockk()
    private val journalpostBrukerService: JournalpostBrukerService =
        JournalpostBrukerService(
            pdlClient = mockedPdlClient,
        )

    @Test
    fun `skal finne bruker ident for AKTOERID`() {
        // Arrange
        val bruker =
            Bruker(
                id = "1",
                type = BrukerIdType.AKTOERID,
            )

        every {
            mockedPdlClient.hentPersonident(
                aktørId = bruker.id,
                tema = Tema.BAR,
            )
        } returns "2"

        // Act
        val brukerIdent =
            journalpostBrukerService.tilPersonIdent(
                bruker = bruker,
                tema = Tema.BAR,
            )

        // Assert
        assertThat(brukerIdent).isEqualTo("2")
    }

    @Test
    fun `skal returnere fnr som brukers ident for bruker med fnr`() {
        // Arrange
        val bruker =
            Bruker(
                id = "1",
                type = BrukerIdType.FNR,
            )

        // Act
        val brukerIdent =
            journalpostBrukerService.tilPersonIdent(
                bruker = bruker,
                tema = Tema.BAR,
            )

        // Assert
        assertThat(brukerIdent).isEqualTo("1")
    }

    @Test
    fun `skal returnere orgnr som brukers ident for bruker med orgn`() {
        // Arrange
        val bruker =
            Bruker(
                id = "9",
                type = BrukerIdType.ORGNR,
            )

        // Act
        val brukerIdent =
            journalpostBrukerService.tilPersonIdent(
                bruker = bruker,
                tema = Tema.BAR,
            )

        // Assert
        assertThat(brukerIdent).isEqualTo("9")
    }
}
