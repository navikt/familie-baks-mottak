package no.nav.familie.ba.mottak.søknad


import no.nav.familie.ba.mottak.DevLauncherPostgres
import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import no.nav.familie.ba.mottak.task.JournalførSøknadTask
import no.nav.familie.ba.mottak.util.DbContainerInitializer
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
import org.springframework.web.client.HttpClientErrorException
import java.util.*
import kotlin.test.*


@ExtendWith(SpringExtension::class)
@ContextConfiguration(initializers = [DbContainerInitializer::class])
@ActiveProfiles("postgres", "mock-dokarkiv-conflict", "mock-dokgen")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class EksternReferanseIdTest(
    @Autowired
    val søknadService: SøknadService,
    @Autowired
    val journalførSøknadTask: JournalførSøknadTask
) {

    val søknad = SøknadTestData.søknad()
    val dbSøknad = søknad.tilDBSøknad()

    @Test
    fun `ved (409 Conflict) fra dokarkiv skal HttpClientErrorException Conflict catches og håndteres i task'en`() {
        val dbSøknadFraDBFirst = søknadService.lagreDBSøknad(dbSøknad.copy(journalpostId = null))
        val properties = Properties().apply { this["søkersFødselsnummer"] = dbSøknadFraDBFirst.fnr }

        assertDoesNotThrow {
            journalførSøknadTask.doTask(
                Task.nyTask(
                    JournalførSøknadTask.JOURNALFØR_SØKNAD,
                    dbSøknadFraDBFirst.id.toString(),
                    properties
                )
            )
        }
    }
}
