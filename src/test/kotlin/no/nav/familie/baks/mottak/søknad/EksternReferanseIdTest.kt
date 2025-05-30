package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.DevLauncherPostgres
import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.baks.mottak.task.JournalførSøknadTask
import no.nav.familie.baks.mottak.util.DbContainerInitializer
import no.nav.familie.prosessering.domene.Task
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.Properties

@ExtendWith(SpringExtension::class)
@ContextConfiguration(initializers = [DbContainerInitializer::class])
@ActiveProfiles("postgres", "mock-dokarkiv-conflict", "mock-dokgen", "mock-familie-pdf")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class EksternReferanseIdTest(
    @Autowired
    val barnetrygdSøknadService: BarnetrygdSøknadService,
    @Autowired
    val journalførSøknadTask: JournalførSøknadTask,
) {
    val søknad = SøknadTestData.barnetrygdSøknad()
    val dbSøknad = søknad.tilDBSøknad()

    @Test
    fun `ved (409 Conflict) fra dokarkiv skal HttpClientErrorException Conflict catches og håndteres i task'en`() {
        val dbSøknadFraDBFirst = barnetrygdSøknadService.lagreDBSøknad(dbSøknad.copy(journalpostId = null))
        val properties = Properties().apply { this["søkersFødselsnummer"] = dbSøknadFraDBFirst.fnr }

        assertDoesNotThrow {
            journalførSøknadTask.doTask(
                Task(
                    type = JournalførSøknadTask.JOURNALFØR_SØKNAD,
                    payload = dbSøknadFraDBFirst.id.toString(),
                    properties = properties,
                ),
            )
        }
    }
}
