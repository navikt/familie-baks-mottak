package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DBSøknadTest {
    val søknad = SøknadTestData.søknad()

    @Test
    fun `Lagring av søknad og mapping tilbake gir like objekt`() {
        val dbSøknad = søknad.tilDBSøknad()
        assertEquals(søknad, dbSøknad.hentSøknad())
    }
}