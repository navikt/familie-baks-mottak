package no.nav.familie.ba.mottak.søknad

import no.nav.familie.kontrakter.ba.søknad.Søknadsfelt
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Feltformaterer {

    /**
     * Håndterer formatering utover vanlig toString for endenodene
     */
    fun mapEndenodeTilUtskriftMap(entitet: Søknadsfelt<*>): Map<String, String> {
        return feltMap(entitet.label, mapVerdi(entitet.verdi!!))
    }

    private fun mapVerdi(verdi: Any): String {
        return when (verdi) {
            is Month ->
                displayName(verdi)
            is Boolean ->
                if (verdi) "Ja" else "Nei"
            is List<*> ->
                verdi.joinToString("\n\n") { mapVerdi(it!!) }
            is LocalDate ->
                verdi.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            is LocalDateTime ->
                verdi.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
            else ->
                verdi.toString()
        }
    }

    private fun displayName(verdi: Month) = verdi.getDisplayName(TextStyle.FULL, Locale("no"))

    private fun feltMap(label: String, verdi: String) = mapOf("label" to label, "verdi" to verdi)
}
