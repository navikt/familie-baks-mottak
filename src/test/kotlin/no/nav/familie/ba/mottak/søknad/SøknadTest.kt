package no.nav.familie.ba.mottak.søknad


import no.nav.familie.ba.mottak.DevLauncherPostgres
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import no.nav.familie.ba.mottak.util.DbContainerInitializer
import no.nav.familie.kontrakter.felles.objectMapper
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
@ActiveProfiles("postgres")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class SøknadTest(
        @Autowired val søknadService: SøknadService,
) {

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
    fun `Version detection ved henting av søknad fra database`() {
        val lagraV5SøknadData = objectMapper.writeValueAsString(SøknadTestData.tomv5søknad())
        val dbSøknad = DBSøknad(
            id = 1L,
            søknadJson = lagraV5SøknadData,
            fnr = "1234578901",
        )
        assertEquals("v5", dbSøknad.hentSøknadVersjon())

        val lagraV6SøknadData = objectMapper.writeValueAsString(SøknadTestData.søknad())
        val v6dbSøknad = DBSøknad(
            id = 2L,
            søknadJson = lagraV6SøknadData,
            fnr = "1234123412",
        )
        assertEquals("v6", v6dbSøknad.hentSøknadVersjon())
    }
}