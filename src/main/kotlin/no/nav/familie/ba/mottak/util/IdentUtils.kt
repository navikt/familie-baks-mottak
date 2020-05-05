package no.nav.familie.ba.mottak.util

import no.nav.familie.ba.mottak.domene.personopplysning.PersonIdent

fun erDnummer(personIdent: PersonIdent): Boolean {
    return if (personIdent.id == null) false else erDnummer(personIdent.id!!)
}

fun erDnummer(personIdent: String): Boolean {
    return personIdent.substring(0, 1).toInt() > 3
}