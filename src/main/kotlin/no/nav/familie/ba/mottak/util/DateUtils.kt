package no.nav.familie.ba.mottak.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month


fun fristFerdigstillelse(daysToAdd: Long = 0): LocalDate {
    var date = LocalDateTime.now().plusDays(daysToAdd)

    if (date.hour >= 14) {
        date = date.plusDays(1)
    }

    when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> date = date.plusDays(2)
        DayOfWeek.SUNDAY -> date = date.plusDays(1)
    }

    when {
        date.dayOfMonth == 1 && date.month == Month.JANUARY -> date = date.plusDays(1)
        date.dayOfMonth == 1 && date.month == Month.MAY -> date = date.plusDays(1)
        date.dayOfMonth == 17 && date.month == Month.MAY -> date = date.plusDays(1)
        date.dayOfMonth == 25 && date.month == Month.DECEMBER -> date = date.plusDays(2)
        date.dayOfMonth == 26 && date.month == Month.DECEMBER -> date = date.plusDays(1)
    }

    when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> date = date.plusDays(2)
        DayOfWeek.SUNDAY -> date = date.plusDays(1)
    }

    return date.toLocalDate()
}
