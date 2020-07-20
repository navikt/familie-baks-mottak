package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.FødselsnummerErNullException
import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DBSøknadTest {
    val søknad = SøknadTestData.søknad()

    @Test
    fun `Lagring av søknad og mapping tilbake gir like objekt`() {
        val dbSøknad = søknad.tilDBSøknad()
        assertEquals(søknad, dbSøknad.hentSøknad())
    }

    @Test
    fun `Lagring av søknad uten fnr kaster korrekt feilmelding`() {
        val søknadUtenFnr = søknad.copy( søker = søknad.søker.copy(fødselsnummer = null) )
        assertFailsWith<FødselsnummerErNullException> { søknadUtenFnr.tilDBSøknad() }
    }
}