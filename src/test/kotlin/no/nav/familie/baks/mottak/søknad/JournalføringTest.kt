package no.nav.familie.baks.mottak.søknad

import io.mockk.junit5.MockKExtension
import no.nav.familie.baks.mottak.DevLauncherPostgres
import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadTestData
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.tilDBKontantstøtteSøknad
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

@ExtendWith(SpringExtension::class, MockKExtension::class)
@ContextConfiguration(initializers = [DbContainerInitializer::class])
@ActiveProfiles("postgres", "mock-dokarkiv")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class JournalføringTest(
    @Autowired
    val journalføringService: JournalføringService,
    @Autowired
    val barnetrygdSøknadService: BarnetrygdSøknadService,
    @Autowired
    val kontantstøtteSøknadService: KontantstøtteSøknadService,
) {

    val søknad = SøknadTestData.søknadV8()
    val dbSøknad = søknad.tilDBSøknad()
    val kontantstøtteSøknad = KontantstøtteSøknadTestData.kontantstøtteSøknad()
    val dbKontantstøtteSøknad = kontantstøtteSøknad.tilDBKontantstøtteSøknad()
    val testPDF = "test123".toByteArray()

    @Test
    fun `arkiverSøknad returnerer riktig journalpostId for barnetrygdsøknad`() {
        val arkiverDokumentRequest = ArkiverDokumentRequestMapper.toDto(
            dbSøknad = dbSøknad,
            versjonertSøknad = dbSøknad.hentVersjonertSøknad(),
            pdf = testPDF,
            vedleggMap = emptyMap(),
            pdfOriginalSpråk = ByteArray(0),
        )
        val journalPostId = journalføringService.arkiverSøknad(arkiverDokumentRequest)

        assertEquals("123", journalPostId)
    }

    @Test
    fun `arkiverSøknad returnerer riktig journalpostId for kontantstøttesøknad`() {
        val arkiverDokumentRequest = ArkiverDokumentRequestMapper.toDto(
            dbKontantstøtteSøknad = dbKontantstøtteSøknad,
            versjonertSøknad = dbKontantstøtteSøknad.hentVersjonertKontantstøtteSøknad(),
            pdf = testPDF,
            vedleggMap = emptyMap(),
            pdfOriginalSpråk = ByteArray(0),
        )
        val journalPostId = journalføringService.arkiverSøknad(arkiverDokumentRequest)

        assertEquals("123", journalPostId)
    }

    @Test
    fun `journalPostId blir lagt på dbSøknaden`() {
        val dbSøknadFraDB = barnetrygdSøknadService.lagreDBSøknad(dbSøknad)
        assertEquals(null, dbSøknadFraDB.journalpostId)
        journalføringService.journalførBarnetrygdSøknad(dbSøknadFraDB, testPDF)
        val overskrevetDBSøknad = barnetrygdSøknadService.hentDBSøknad(dbSøknadFraDB.id)

        assertEquals("123", overskrevetDBSøknad!!.journalpostId)
    }

    @Test
    fun `journalPostId blir lagt på dbKontantstøtteSøknad`() {
        val dbKontantstøtteSøknadFraDB = kontantstøtteSøknadService.lagreDBKontantstøtteSøknad(dbKontantstøtteSøknad)
        assertEquals(null, dbKontantstøtteSøknadFraDB.journalpostId)
        journalføringService.journalførKontantstøtteSøknad(dbKontantstøtteSøknadFraDB, testPDF)
        val overskrevetDBKontantstøtteSøknad =
            kontantstøtteSøknadService.hentDBKontantstøtteSøknad(dbKontantstøtteSøknadFraDB.id)

        assertEquals("123", overskrevetDBKontantstøtteSøknad!!.journalpostId)
    }

    @Test
    fun `journalPostId skal ikke bli satt hvis den allerede finnes på dbSøknad`() {
        val dbSøknadMedJournalpostId = dbSøknad.copy(journalpostId = "1")
        val dbSøknadFraDB = barnetrygdSøknadService.lagreDBSøknad(dbSøknadMedJournalpostId)

        journalføringService.journalførBarnetrygdSøknad(dbSøknadFraDB, testPDF)
        val ikkeOverskrevetDBSøknad = barnetrygdSøknadService.hentDBSøknad(dbSøknadFraDB.id)

        assertEquals("1", ikkeOverskrevetDBSøknad!!.journalpostId)
    }

    @Test
    fun `journalPostId skal ikke bli satt hvis den allerede finnes på dbKontantstøtteSøknad`() {
        val dbKontantstøtteSøknadMedJournalpostId = dbKontantstøtteSøknad.copy(journalpostId = "1")
        val dbKontantstøtteSøknadFraDB =
            kontantstøtteSøknadService.lagreDBKontantstøtteSøknad(dbKontantstøtteSøknadMedJournalpostId)

        journalføringService.journalførKontantstøtteSøknad(dbKontantstøtteSøknadFraDB, testPDF)
        val ikkeOverskrevetDBSøknad =
            kontantstøtteSøknadService.hentDBKontantstøtteSøknad(dbKontantstøtteSøknadFraDB.id)

        assertEquals("1", ikkeOverskrevetDBSøknad!!.journalpostId)
    }
}
