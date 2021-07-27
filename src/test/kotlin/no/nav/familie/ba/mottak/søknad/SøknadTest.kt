package no.nav.familie.ba.mottak.søknad


import com.fasterxml.jackson.core.type.TypeReference
import no.nav.familie.ba.mottak.DevLauncherPostgres
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import no.nav.familie.ba.mottak.util.DbContainerInitializer
import no.nav.familie.kontrakter.felles.objectMapper
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import kotlin.test.assertEquals
import kotlin.test.assertNull


@ExtendWith(SpringExtension::class)
@ContextConfiguration(initializers = [DbContainerInitializer::class])
@ActiveProfiles("postgres")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class SøknadTest(
        @Autowired
        val søknadService: SøknadService) {

    val søknad = SøknadTestData.søknad()

    @Test
    fun `Lagring av søknad`() {
        val dbSøknadFraMapper = søknad.tilDBSøknad()
        assertEquals(søknad, dbSøknadFraMapper.hentSøknad())

        val dbSøknadFraDB = søknadService.lagreDBSøknad(dbSøknadFraMapper)
        val hentetSøknad = søknadService.hentDBSøknad(dbSøknadFraDB.id)
        assertEquals(dbSøknadFraDB.id, hentetSøknad!!.id)
        assertEquals(
                dbSøknadFraDB.hentSøknad(),
                hentetSøknad.hentSøknad()
        )
    }

    @Test
    fun `Henting av V1 søknad til V2 objekt`() {
        val lagraSøknadData = objectMapper.writeValueAsString(SøknadTestData.søknadV1())
        val dbSøknad = DBSøknad(
            id = 1L,
            søknadJson = lagraSøknadData,
            fnr = "1234578901",
        )
        assertDoesNotThrow {
            val hentetSøknad = dbSøknad.hentSøknad()
        }
        val hentetSøknad = dbSøknad.hentSøknad()
        val søkerAsMap = objectMapper.convertValue(hentetSøknad.søker, object: TypeReference<Map<String, Any>>(){})

        assertNull(søkerAsMap["telefonnummer"]);
    }
}