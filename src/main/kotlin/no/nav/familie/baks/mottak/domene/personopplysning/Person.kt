package no.nav.familie.baks.mottak.domene.personopplysning

import no.nav.familie.baks.mottak.integrasjoner.Adressebeskyttelsesgradering
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.kontrakter.felles.personopplysning.ForelderBarnRelasjon

data class Person(
    val navn: String?,
    val forelderBarnRelasjoner: Set<ForelderBarnRelasjon>,
    val bostedsadresse: Bostedsadresse? = null,
    val adressebeskyttelseGradering: List<Adressebeskyttelsesgradering>,
)

data class Familierelasjon(
    val personIdent: PersonIdent,
    val relasjonsrolle: String,
)

data class PersonIdent(
    val id: String,
)

fun Person.harAdresseGradering(): Boolean =
    this.adressebeskyttelseGradering.any { it.erFortrolig() || it.erStrengtFortrolig() }

fun Person.harBostedsadresse(): Boolean = this.bostedsadresse != null
