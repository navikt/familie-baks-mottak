package no.nav.familie.ba.mottak.domene.personopplysning

import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse


data class Person(
        val navn: String,
        val familierelasjoner: Set<Familierelasjon>,
        val bostedsadresse: Bostedsadresse? = null
)

data class Familierelasjon(
        val personIdent: PersonIdent,
        val relasjonsrolle: String
)

data class PersonIdent(
        val id: String
)



