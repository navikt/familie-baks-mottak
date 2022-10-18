package no.nav.familie.baks.mottak.util

import no.nav.familie.baks.mottak.domene.personopplysning.PersonIdent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class IdentUtilsTest {
    @Test
    fun testErDnummer() {
        Assertions.assertFalse(erDnummer(PersonIdent(id = "12345678910")))
        Assertions.assertTrue(erDnummer(PersonIdent(id = "42345678910")))
    }

    @Test
    fun `er BOST-nummer`() {
        Assertions.assertFalse(erBostNummer(PersonIdent(id = "10102022222")))
        Assertions.assertTrue(erBostNummer(PersonIdent(id = "10201022222")))
    }

    @Test
    fun `er FDAT nummer`() {
        Assertions.assertFalse(erFDatnummer(PersonIdent(id = "10102022222")))
        Assertions.assertTrue(erFDatnummer(PersonIdent(id = "10201000000")))
    }
}
