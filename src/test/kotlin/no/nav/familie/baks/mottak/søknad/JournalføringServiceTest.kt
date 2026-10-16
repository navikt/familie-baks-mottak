package no.nav.familie.baks.mottak.søknad

import io.mockk.junit5.MockKExtension
import no.nav.familie.baks.mottak.DevLauncherPostgres
import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBVedlegg
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadVedleggRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadTestData
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstotteVedlegg
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteVedleggRepository
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.tilDBKontantstøtteSøknad
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(SpringExtension::class, MockKExtension::class)
@ActiveProfiles("postgres", "mock-dokarkiv", "testcontainers")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
@MockKExtension.KeepMocks
class JournalføringServiceTest(
    @Autowired
    val journalføringService: JournalføringService,
    @Autowired
    val barnetrygdSøknadService: BarnetrygdSøknadService,
    @Autowired
    val kontantstøtteSøknadService: KontantstøtteSøknadService,
    @Autowired
    val søknadVedleggRepository: SøknadVedleggRepository,
    @Autowired
    val kontantstøtteVedleggRepository: KontantstøtteVedleggRepository,
) {
    val søknad = SøknadTestData.barnetrygdSøknad()
    val dbSøknad = søknad.tilDBSøknad()
    val kontantstøtteSøknad = KontantstøtteSøknadTestData.kontantstøtteSøknad()
    val dbKontantstøtteSøknad = kontantstøtteSøknad.tilDBKontantstøtteSøknad()
    val testPDF = "test123".toByteArray()

    @Test
    fun `arkiverSøknad returnerer riktig journalpostId for barnetrygdsøknad`() {
        val arkiverDokumentRequest =
            ArkiverDokumentRequestMapper.toDto(
                dbBarnetrygdSøknad = dbSøknad,
                versjonertBarnetrygdSøknad = dbSøknad.hentVersjonertBarnetrygdSøknad(),
                pdf = testPDF,
                vedleggMap = emptyMap(),
                pdfOriginalSpråk = ByteArray(0),
            )
        val journalPostId = journalføringService.arkiverSøknad(arkiverDokumentRequest)

        assertEquals("123", journalPostId)
    }

    @Test
    fun `arkiverSøknad returnerer riktig journalpostId for kontantstøttesøknad`() {
        val arkiverDokumentRequest =
            ArkiverDokumentRequestMapper.toDto(
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
    fun `barnetrygdsøknad og vedlegg slettes fra databasen etter journalføring`() {
        // Arrange
        val dbSøknadFraDB = barnetrygdSøknadService.lagreDBSøknad(dbSøknad)
        søknadVedleggRepository.save(DBVedlegg(dokumentId = UUID.randomUUID().toString(), søknadId = dbSøknadFraDB.id, data = testPDF))

        // Act
        journalføringService.journalførBarnetrygdSøknad(dbSøknadFraDB, testPDF)

        // Assert
        assertNull(barnetrygdSøknadService.hentDBSøknad(dbSøknadFraDB.id))
        assertTrue(søknadVedleggRepository.hentAlleVedlegg(dbSøknadFraDB.id).isEmpty())
    }

    @Test
    fun `kontantstøttesøknad og vedlegg slettes fra databasen etter journalføring`() {
        // Arrange
        val dbKontantstøtteSøknadFraDB = kontantstøtteSøknadService.lagreDBKontantstøtteSøknad(dbKontantstøtteSøknad)
        kontantstøtteVedleggRepository.save(
            DBKontantstotteVedlegg(dokumentId = UUID.randomUUID().toString(), søknadId = dbKontantstøtteSøknadFraDB.id, data = testPDF),
        )

        // Act
        journalføringService.journalførKontantstøtteSøknad(dbKontantstøtteSøknadFraDB, testPDF)

        // Assert
        assertNull(kontantstøtteSøknadService.hentDBKontantstøtteSøknad(dbKontantstøtteSøknadFraDB.id))
        assertTrue(kontantstøtteVedleggRepository.hentAlleVedlegg(dbKontantstøtteSøknadFraDB.id).isEmpty())
    }
}
