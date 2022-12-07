package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.DevLauncherPostgres
import no.nav.familie.baks.mottak.søknad.domene.tilDBSøknad
import no.nav.familie.baks.mottak.util.DbContainerInitializer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import kotlin.test.assertEquals

@ExtendWith(SpringExtension::class)
@ContextConfiguration(initializers = [DbContainerInitializer::class])
@ActiveProfiles("postgres", "mock-dokarkiv")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class JournalføringTest(
    @Autowired
    val journalføringService: JournalføringService,
    @Autowired
    val søknadService: SøknadService
) {

    val søknad = SøknadTestData.søknadV8()
    val testPDF = "test123".toByteArray()
    val dbSøknad = søknad.tilDBSøknad()

    @Test
    fun `arkiverSøknad returnerer riktig journalpostId`() {
        val arkiverDokumentRequest = ArkiverDokumentRequestMapper.toDto(
            dbSøknad = dbSøknad,
            versjonertSøknad = dbSøknad.hentVersjonertSøknad(),
            pdf = testPDF,
            vedleggMap = emptyMap(),
            pdfOriginalSpråk = ByteArray(0)
        )
        val journalPostId = journalføringService.arkiverSøknad(arkiverDokumentRequest)

        assertEquals("123", journalPostId)
    }

    @Test
    fun `journalPostId blir lagt på dbSøknaden`() {
        val dbSøknadFraDB = søknadService.lagreDBSøknad(dbSøknad)
        assertEquals(null, dbSøknadFraDB.journalpostId)
        journalføringService.journalførKontantstøtteSøknad(dbSøknadFraDB, testPDF)
        val overskrevetDBSøknad = søknadService.hentDBSøknad(dbSøknadFraDB.id)

        assertEquals("123", overskrevetDBSøknad!!.journalpostId)
    }

    @Test
    fun `journalPostId skal ikke bli satt hvis den allerede finnes`() {
        val dbSøknadMedJournalpostId = dbSøknad.copy(journalpostId = "1")
        val dbSøknadFraDB = søknadService.lagreDBSøknad(dbSøknadMedJournalpostId)

        journalføringService.journalførKontantstøtteSøknad(dbSøknadFraDB, testPDF)
        val ikkeOverskrevetDBSøknad = søknadService.hentDBSøknad(dbSøknadFraDB.id)

        assertEquals("1", ikkeOverskrevetDBSøknad!!.journalpostId)
    }
}
