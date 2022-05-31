package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.DevLauncherPostgres
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.ba.mottak.søknad.domene.SøknadV7
import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import no.nav.familie.ba.mottak.util.DbContainerInitializer
import no.nav.familie.kontrakter.felles.objectMapper
import org.assertj.core.api.Assertions.assertThat
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

    val søknadV7 = SøknadTestData.søknadV7()

    @Test
    fun `Lagring av søknad`() {
        val dbSøknadFraMapper = søknadV7.tilDBSøknad()
        assertThat(dbSøknadFraMapper.hentVersjonertSøknad() is SøknadV7).isTrue()

        val dbSøknadFraDB = søknadService.lagreDBSøknad(dbSøknadFraMapper)
        val hentetSøknad = søknadService.hentDBSøknad(dbSøknadFraDB.id)
        assertEquals(dbSøknadFraDB.id, hentetSøknad!!.id)
        assertThat(hentetSøknad.hentVersjonertSøknad() is SøknadV7)
    }

    @Test
    fun `Få riktig versjon v7 ved mapping fra DBSøknad`() {
        val dbSøknadFraMapper = søknadV7.tilDBSøknad()
        val versjonertSøknad = dbSøknadFraMapper.hentVersjonertSøknad()
        val versjon: Int? = when (versjonertSøknad) {
            is SøknadV7 -> versjonertSøknad.søknad.kontraktVersjon
            else -> {
                null
            }
        }
        assertEquals(søknadV7.kontraktVersjon, versjon)

        val dbSøknadFraDB = søknadService.lagreDBSøknad(dbSøknadFraMapper)
        val hentetSøknad = søknadService.hentDBSøknad(dbSøknadFraDB.id)
        assertEquals(dbSøknadFraDB.id, hentetSøknad!!.id)
        assertThat(hentetSøknad.hentVersjonertSøknad() is SøknadV7).isTrue
    }

    @Test
    fun `Version detection ved henting av søknad fra database`() {
        val lagraV7SøknadData = objectMapper.writeValueAsString(SøknadTestData.søknadV7())
        val v7dbSøknad = DBSøknad(
            id = 2L,
            søknadJson = lagraV7SøknadData,
            fnr = "1234123412",
        )
        assertThat(v7dbSøknad.hentVersjonertSøknad() is SøknadV7).isTrue
    }
}
