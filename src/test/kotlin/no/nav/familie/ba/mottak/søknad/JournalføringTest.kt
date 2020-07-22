package no.nav.familie.ba.mottak.søknad


import no.nav.familie.ba.mottak.DevLauncherPostgres
import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import no.nav.familie.ba.mottak.util.DbContainerInitializer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import kotlin.test.assertEquals
import kotlin.test.assertFails


@ExtendWith(SpringExtension::class)
@ContextConfiguration(initializers = [DbContainerInitializer::class])
@ActiveProfiles("postgres", "mock-dokarkiv")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class JournalføringTest(
        @Autowired
        val journalføringService: JournalføringService,
        @Autowired
        val søknadService: SøknadService) {

    val søknad = SøknadTestData.søknad()
    val testPDF = "test123".toByteArray()
    val dbSøknad = søknad.tilDBSøknad()

    @Test
    fun `arkiverSøknad returnerer riktig journalpostId`() {
        val journalPostId = journalføringService.arkiverSøknad(dbSøknad, testPDF)

        assertEquals("123", journalPostId)
    }

    @Test
    fun `dbSøknad uten id skal kaste gi error`() {
        assertFails {
            journalføringService.journalførSøknad("-1", testPDF)
        }
    }

    @Test
    fun `journalPostId er lagt på dbSøknaden`() {
        val dbSøknadFraDB = søknadService.lagreDBSøknad(dbSøknad)

        journalføringService.journalførSøknad(dbSøknadFraDB.id.toString(), testPDF)
        val overskrevetDBSøknad = søknadService.hentDBSøknad(dbSøknadFraDB.id)

        assertEquals("123", overskrevetDBSøknad!!.journalpostId)
    }
}