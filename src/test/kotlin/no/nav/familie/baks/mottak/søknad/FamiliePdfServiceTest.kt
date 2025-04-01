package no.nav.familie.baks.mottak.søknad

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class FamiliePdfServiceTest {
    private val pdfClient: FamiliePdfClient = mockk()
    private val familiePdfService = FamiliePdfService(pdfClient)

    @BeforeEach
    fun setup() {
        every { pdfClient.opprettPdf(any()) } returns byteArrayOf(1, 2, 3)
    }

    @Test
    fun `Lag feltmaptest`() {
        val barnetrygdSøknadSomString =
            File("./src/test/kotlin/no/nav/familie/baks/mottak/søknad/testdata/testdata1.json")
                .readText(Charsets.UTF_8)
        val dbBarnetrygdSøknad =
            DBBarnetrygdSøknad(
                id = 2L,
                søknadJson = barnetrygdSøknadSomString,
                fnr = "1234123412",
            )

        val feltmap = familiePdfService.lagBarnetrygdPdfKvittering(dbBarnetrygdSøknad, "nb")

        println("feltmap $feltmap")
    }
}
