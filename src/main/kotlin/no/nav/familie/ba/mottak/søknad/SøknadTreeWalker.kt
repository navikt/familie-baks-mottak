package no.nav.familie.ba.mottak.søknad

import main.kotlin.no.nav.familie.ba.Søknadstype
import main.kotlin.no.nav.familie.ba.søknad.Søknad
import main.kotlin.no.nav.familie.ba.søknad.Søknadsfelt
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor

// SøknadTreeWalker er hentet fra familie-ef-mottak
object SøknadTreeWalker {
    private val endNodes =
            setOf<KClass<*>>(String::class,
                             Int::class,
                             Boolean::class,
                             LocalDate::class,
                             LocalDateTime::class,
                             Month::class,
                             Long::class,
                             Søknadstype::class)

    fun mapSøknadsfelter(søknad: Søknad): Map<String, Any> {
        val finnFelter = finnFelter(søknad)  // TODO: Bruk enum values når det går an
        val søknadskode = søknad.søknadstype.verdi.søknadskode
        return feltlisteMap("Søknad barnetrygd - $søknadskode", finnFelter)
    }

    private fun finnFelter(entitet: Any): List<Map<String, *>> {

        // Det går ikke å hente elementene i en liste med reflection, så vi traverserer den som vanlig.
        if (entitet is List<Any?>) {
            return entitet.filterNotNull()
                    .map { finnFelter(it) }
                    .flatten()
        }
        val parametere = konstruktørparametere(entitet)

        val feltliste = parametere
                .asSequence()
                .map { finnSøknadsfelt(entitet, it) }
                .filter { it.visibility == KVisibility.PUBLIC }
                .mapNotNull { getFeltverdi(it, entitet) }
                .map { finnFelter(it) } // Kall rekursivt videre
                .flatten()
                .toList()

        if (entitet is Søknadsfelt<*>) {
            if (entitet.verdi!!::class in endNodes) {
                return listOf(Feltformaterer.mapEndenodeTilUtskriftMap(entitet))
            }
            if (entitet.verdi is List<*>) {
                val verdiliste = entitet.verdi as List<*>
                if (verdiliste.isNotEmpty() && verdiliste.first() is String) {
                    return listOf(Feltformaterer.mapEndenodeTilUtskriftMap(entitet))
                }
            }
            return listOf(feltlisteMap(entitet.label, feltliste))
        }
        return feltliste
    }

    private fun feltlisteMap(label: String, verdi: List<*>) = mapOf("label" to label, "verdiliste" to verdi)

    /**
     * Henter ut verdien for felt på entitet.
     */
    private fun getFeltverdi(felt: KProperty1<out Any, Any?>, entitet: Any) =
            felt.getter.call(entitet)

    /**
     * Finn første (og eneste) felt på entiteten som har samme navn som konstruktørparameter.
     */
    private fun finnSøknadsfelt(entity: Any, konstruktørparameter: KParameter) =
            entity::class.declaredMemberProperties.first { it.name == konstruktørparameter.name }

    /**
     * Konstruktørparametere er det eneste som gir oss en garantert rekkefølge for feltene, så vi henter disse først.
     */
    private fun konstruktørparametere(entity: Any) = entity::class.primaryConstructor?.parameters ?: emptyList()

}
