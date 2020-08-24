package no.nav.familie.ba.mottak.util

import no.nav.familie.ba.mottak.domene.personopplysning.PersonIdent

fun erDnummer(personIdent: PersonIdent): Boolean {
    return if (personIdent.id == null) false else erDnummer(personIdent.id!!)
}

fun erDnummer(personIdent: String): Boolean {
    return personIdent.substring(0, 1).toInt() > 3
}

fun erFDatnummer(personIdent: String): Boolean {
    return personIdent.substring(6)?.toInt()!! == 0
}

fun erFDatnummer(personIdent: PersonIdent): Boolean {
    return if (personIdent.id == null) false else erFDatnummer(personIdent.id!!)
}

fun erBostNummer(personIdent: String): Boolean {
    return personIdent.substring(2, 3)?.toInt()!! > 1
}

fun erBostNummer(personIdent: PersonIdent): Boolean {
    return if (personIdent.id == null) false else erBostNummer(personIdent.id!!)
}