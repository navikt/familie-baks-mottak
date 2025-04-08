package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.domene.LabelVerdiPar
import no.nav.familie.baks.mottak.domene.VerdilisteElement
import no.nav.familie.kontrakter.felles.Fødselsnummer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor

private val endNodes =
    setOf<KClass<*>>(
        String::class,
        Int::class,
        Boolean::class,
        Double::class,
        Fødselsnummer::class,
        LocalDate::class,
        LocalDateTime::class,
        Month::class,
        Long::class,
    )

fun finnFelter(entitet: Any): List<VerdilisteElement> {
    if (entitet is List<Any?>) {
        return entitet
            .filterNotNull()
            .map { finnFelter(it) }
            .flatten()
    }

    val parametere = konstruktørparametere(entitet)

    val list =
        parametere
            .asSequence()
            .map { finnSøknadsfelt(entitet, it) }
            .filter { it.visibility == KVisibility.PUBLIC }
            .mapNotNull { getFeltverdi(it, entitet) }
            .map { finnFelter(it) } // Kall rekursivt videre
            .flatten()
            .toList()

    if (entitet is LabelVerdiPar<*>) {
        if (entitet.verdi!!::class in endNodes) {
            return maptilVerdilisteElement(entitet)?.let { listOf(it) } ?: emptyList()
        }

        if (entitet.verdi is List<*>) {
            val verdiliste = entitet.verdi

            if (verdiliste.firstOrNull() is String) {
                return maptilVerdilisteElement(entitet)?.let { listOf(it) } ?: emptyList()
            }
        }

        if (list.size == 1 && list.first().verdiliste.isNullOrEmpty() && list.first().verdi.isNullOrEmpty()) {
            return emptyList()
        }

        return listOf(VerdilisteElement(label = entitet.label, verdiliste = list))
    }

//    println("hei" + list)
    return list
}

fun maptilVerdilisteElement(labelVerdiPar: LabelVerdiPar<*>): VerdilisteElement? = VerdilisteElement(label = labelVerdiPar.label, verdi = mapVerdi(labelVerdiPar.verdi))

fun mapVerdi(verdi: Any?): String {
    return when (verdi) {
        is String -> return verdi

        else -> {
            verdi.toString()
        }
    }
}

private fun konstruktørparametere(entity: Any) = entity::class.primaryConstructor?.parameters ?: emptyList()

private fun getFeltverdi(
    felt: KProperty1<out Any, Any?>,
    entitet: Any,
) = felt.getter.call(entitet)

private fun finnSøknadsfelt(
    entity: Any,
    konstruktørparameter: KParameter,
) = entity::class.declaredMemberProperties.first { it.name == konstruktørparameter.name }
