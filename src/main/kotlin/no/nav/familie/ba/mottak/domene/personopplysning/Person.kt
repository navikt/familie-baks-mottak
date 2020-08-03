package no.nav.familie.ba.mottak.domene.personopplysning


data class Person(
        val navn: String,
        val familierelasjoner: Set<Familierelasjon>
)

data class Familierelasjon(
        val personIdent: PersonIdent,
        val relasjonsrolle: String
)

data class PersonIdent(
        val id: String
)



