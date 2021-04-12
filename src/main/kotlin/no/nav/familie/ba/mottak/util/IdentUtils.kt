package no.nav.familie.ba.mottak.util

import no.nav.familie.ba.mottak.domene.personopplysning.PersonIdent

fun erDnummer(personIdent: PersonIdent): Boolean {
    return erDnummer(personIdent.id)
}

fun erDnummer(personIdent: String): Boolean {
    return personIdent.substring(0, 1).toInt() > 3
}

fun erFDatnummer(personIdent: String): Boolean {
    return personIdent.substring(6).toInt() == 0
}

fun erFDatnummer(personIdent: PersonIdent): Boolean {
    return erFDatnummer(personIdent.id)
}

fun erBostNummer(personIdent: String): Boolean {
    return personIdent.substring(2, 3).toInt() > 1
}

fun erBostNummer(personIdent: PersonIdent): Boolean {
    return erBostNummer(personIdent.id)
}

fun erOrgnr(orgNr: String): Boolean {
    if (orgNr.length != 9) {
        return false
    }
    if (!(orgNr.startsWith("8") || orgNr.startsWith("9"))) {
        return false
    }
    return true
}