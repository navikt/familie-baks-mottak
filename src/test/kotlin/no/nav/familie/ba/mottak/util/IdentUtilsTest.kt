package no.nav.familie.ba.mottak.util

import no.nav.familie.ba.mottak.domene.personopplysning.PersonIdent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev")
class IdentUtilsTest {
    @Test
    fun testErDnummer() {
        Assertions.assertFalse(erDnummer(PersonIdent(id = null)))
        Assertions.assertFalse(erDnummer(PersonIdent(id = "12345678910")))
        Assertions.assertTrue(erDnummer(PersonIdent(id = "42345678910")))
    }

}