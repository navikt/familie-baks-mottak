package no.nav.familie.baks.mottak.søknad

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach

internal class FamiliePdfServiceTest {
    private val pdfClient: FamiliePdfClient = mockk()
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService = SøknadSpråkvelgerService()
    private val familiePdfService = FamiliePdfService(pdfClient, søknadSpråkvelgerService)

    @BeforeEach
    fun setup() {
        every { pdfClient.opprettPdf(any()) } returns byteArrayOf(1, 2, 3)
    }

//    @Test
//    fun `Lag feltmaptest`() {
//        val barnetrygdSøknadSomString =
//            File("./src/test/resources/testdata/versjonertBarnetrygdSøknadV9.json")
//                .readText(Charsets.UTF_8)
//        val dbBarnetrygdSøknad =
//            DBBarnetrygdSøknad(
//                id = 2L,
//                søknadJson = barnetrygdSøknadSomString,
//                fnr = "1234123412",
//            )
//
//        val versjonert = dbBarnetrygdSøknad.hentVersjonertBarnetrygdSøknad()
//
//        val feltmap = familiePdfService.lagBarnetrygdPdfKvittering(dbBarnetrygdSøknad, "nb")
//    }
}
