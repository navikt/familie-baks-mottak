package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.DevLauncherPostgres
import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.baks.mottak.util.DbContainerInitializer
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
    @Autowired val barnetrygdSøknadService: BarnetrygdSøknadService,
) {
    val søknadV8 = SøknadTestData.søknadV8()

    @Test
    fun `Lagring av søknad`() {
        val dbSøknadFraMapper = søknadV8.tilDBSøknad()
        assertThat(dbSøknadFraMapper.hentVersjonertSøknad() is SøknadV8).isTrue

        val dbSøknadFraDB = barnetrygdSøknadService.lagreDBSøknad(dbSøknadFraMapper)
        val hentetSøknad = barnetrygdSøknadService.hentDBSøknad(dbSøknadFraDB.id)
        assertEquals(dbSøknadFraDB.id, hentetSøknad!!.id)
        assertThat(hentetSøknad.hentVersjonertSøknad() is SøknadV8)
    }

    @Test
    fun `Få riktig versjon v8 ved mapping fra DBSøknad`() {
        val dbSøknadFraMapper = søknadV8.tilDBSøknad()
        val versjon: Int? =
            when (val versjonertSøknad = dbSøknadFraMapper.hentVersjonertSøknad()) {
                is SøknadV8 -> versjonertSøknad.søknad.kontraktVersjon
                else -> {
                    null
                }
            }
        assertEquals(søknadV8.kontraktVersjon, versjon)

        val dbSøknadFraDB = barnetrygdSøknadService.lagreDBSøknad(dbSøknadFraMapper)
        val hentetSøknad = barnetrygdSøknadService.hentDBSøknad(dbSøknadFraDB.id)
        assertEquals(dbSøknadFraDB.id, hentetSøknad!!.id)
        assertThat(hentetSøknad.hentVersjonertSøknad() is SøknadV8).isTrue
    }

    @Test
    fun `Version detection ved henting av søknad fra database`() {
        val lagraV8SøknadData = objectMapper.writeValueAsString(SøknadTestData.søknadV8())
        val v8DbBarnetrygdSøknad =
            DBBarnetrygdSøknad(
                id = 2L,
                søknadJson = lagraV8SøknadData,
                fnr = "1234123412",
            )
        assertThat(v8DbBarnetrygdSøknad.hentVersjonertSøknad() is SøknadV8).isTrue
    }
}
