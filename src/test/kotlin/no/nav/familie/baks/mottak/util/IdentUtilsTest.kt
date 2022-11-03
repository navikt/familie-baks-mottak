package no.nav.familie.baks.mottak.util

import no.nav.familie.baks.mottak.domene.personopplysning.PersonIdent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class IdentUtilsTest {
    @Test
    fun testErDnummer() {
        Assertions.assertFalse(erDnummer(PersonIdent(id = FNR)))
        Assertions.assertFalse(erDnummer(PersonIdent(id = NAV_SYNTETISK_ID)))
        Assertions.assertTrue(erDnummer(PersonIdent(id = DNR)))
    }

    @Test
    fun `er BOST-nummer`() {
        Assertions.assertFalse(erBostNummer(PersonIdent(id = FNR)))
        Assertions.assertFalse(erBostNummer(PersonIdent(id = NAV_SYNTETISK_ID)))
        Assertions.assertTrue(erBostNummer(PersonIdent(id = BOST_NR)))
    }

    @Test
    fun `er FDAT nummer`() {
        Assertions.assertFalse(erFDatnummer(PersonIdent(id = FNR)))
        Assertions.assertFalse(erFDatnummer(PersonIdent(id = NAV_SYNTETISK_ID)))
        Assertions.assertTrue(erFDatnummer(PersonIdent(id = FDAT_NR)))
    }

    companion object {
        const val BOST_NR = "10211022222"
        const val NAV_SYNTETISK_ID = "31502200112"
        const val FDAT_NR = "10201000000"
        const val FNR = "10102022222"
        const val DNR = "42345678910"
    }
}
