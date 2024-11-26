package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.DevLauncherPostgres
import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.baks.mottak.util.DbContainerInitializer
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV9
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
    val barnetrygdSøknad = SøknadTestData.barnetrygdSøknad()

    @Test
    fun `Lagring av søknad`() {
        val dbSøknadFraMapper = barnetrygdSøknad.tilDBSøknad()
        assertThat(dbSøknadFraMapper.hentVersjonertBarnetrygdSøknad() is VersjonertBarnetrygdSøknadV9).isTrue

        val dbSøknadFraDB = barnetrygdSøknadService.lagreDBSøknad(dbSøknadFraMapper)
        val hentetSøknad = barnetrygdSøknadService.hentDBSøknad(dbSøknadFraDB.id)
        assertEquals(dbSøknadFraDB.id, hentetSøknad!!.id)
        assertThat(hentetSøknad.hentVersjonertBarnetrygdSøknad() is VersjonertBarnetrygdSøknadV9)
    }

    @Test
    fun `Få riktig versjon 9 ved mapping fra DBSøknad`() {
        val dbSøknadFraMapper = barnetrygdSøknad.tilDBSøknad()
        val versjon: Int? =
            when (val versjonertSøknad = dbSøknadFraMapper.hentVersjonertBarnetrygdSøknad()) {
                is VersjonertBarnetrygdSøknadV9 -> versjonertSøknad.barnetrygdSøknad.kontraktVersjon
                else -> {
                    null
                }
            }
        assertEquals(barnetrygdSøknad.kontraktVersjon, versjon)

        val dbSøknadFraDB = barnetrygdSøknadService.lagreDBSøknad(dbSøknadFraMapper)
        val hentetSøknad = barnetrygdSøknadService.hentDBSøknad(dbSøknadFraDB.id)
        assertEquals(dbSøknadFraDB.id, hentetSøknad!!.id)
        assertThat(hentetSøknad.hentVersjonertBarnetrygdSøknad() is VersjonertBarnetrygdSøknadV9).isTrue
    }

    @Test
    fun `Version detection ved henting av søknad fra database`() {
        val barnetrygdSøknadSomString = objectMapper.writeValueAsString(SøknadTestData.barnetrygdSøknad())
        val dbBarnetrygdSøknad =
            DBBarnetrygdSøknad(
                id = 2L,
                søknadJson = barnetrygdSøknadSomString,
                fnr = "1234123412",
            )
        assertThat(dbBarnetrygdSøknad.hentVersjonertBarnetrygdSøknad() is VersjonertBarnetrygdSøknadV9).isTrue
    }
}
