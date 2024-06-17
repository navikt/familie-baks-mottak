package no.nav.familie.baks.mottak.util

import no.nav.familie.baks.mottak.domene.personopplysning.PersonIdent

fun erDnummer(personIdent: PersonIdent): Boolean = erDnummer(personIdent.id)

fun erDnummer(personIdent: String): Boolean = personIdent.substring(0, 1).toInt() > 3

fun erFDatnummer(personIdent: String): Boolean = personIdent.substring(6).toInt() == 0

fun erFDatnummer(personIdent: PersonIdent): Boolean = erFDatnummer(personIdent.id)

/**
 * BOST-nr har måned mellom 21 og 32
 */
fun erBostNummer(personIdent: String): Boolean {
    personIdent.substring(2, 4).toInt().also { måned ->
        return måned in 21..32
    }
}

fun erBostNummer(personIdent: PersonIdent): Boolean = erBostNummer(personIdent.id)

fun erOrgnr(orgNr: String): Boolean {
    if (orgNr.length != 9) {
        return false
    }
    if (!(orgNr.startsWith("8") || orgNr.startsWith("9"))) {
        return false
    }
    return true
}
