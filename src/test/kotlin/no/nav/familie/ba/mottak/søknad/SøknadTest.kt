package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.DevLauncherPostgres
import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import no.nav.familie.ba.mottak.util.DbContainerInitializer
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
        @Autowired
        val søknadService: SøknadService) {

    val søknad = SøknadTestData.søknad()

    @Test
    fun `Lagring av søknad`() {
        val dbSøknadFraMapper = søknad.tilDBSøknad()
        assertEquals(søknad, dbSøknadFraMapper.hentSøknad())

        val dbSøknadFraDB = søknadService.lagreSøknad(søknad)
        val hentetSøknad = søknadService.hentDBSøknad(dbSøknadFraDB.id)
        assertEquals(dbSøknadFraDB.id, hentetSøknad!!.id)
        assertEquals(
                dbSøknadFraDB.hentSøknad(),
                hentetSøknad.hentSøknad()
        )
    }
}