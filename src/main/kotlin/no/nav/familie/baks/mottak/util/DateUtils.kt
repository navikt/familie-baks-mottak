package no.nav.familie.baks.mottak.util

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
        else -> {
            // NOP
        }
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
        else -> {
            // NOP
        }
    }

    return date.toLocalDate()
}

fun nesteGyldigeTriggertidFødselshendelser(
    minutesToAdd: Long = 0,
): LocalDateTime {
    var date = LocalDateTime.now().plusMinutes(minutesToAdd)

    when {
        date.dayOfWeek == DayOfWeek.SATURDAY -> date = date.plusDays(2)
        date.dayOfWeek == DayOfWeek.SUNDAY -> date = date.plusDays(1)
        date.dayOfWeek == DayOfWeek.FRIDAY && date.hour >= 16 -> date = date.plusHours(56)
    }

    when {
        date.dayOfMonth == 1 && date.month == Month.JANUARY -> date = date.plusDays(1)
        date.dayOfMonth == 1 && date.month == Month.MAY -> date = date.plusDays(1)
        date.dayOfMonth == 17 && date.month == Month.MAY -> date = date.plusDays(1).withHour(10)
        date.dayOfMonth == 25 && date.month == Month.DECEMBER -> date = date.plusDays(2).withHour(10)
        date.dayOfMonth == 26 && date.month == Month.DECEMBER -> date = date.plusDays(1).withHour(10)
    }

    when {
        date.dayOfWeek == DayOfWeek.SATURDAY -> date = date.plusDays(2)
        date.dayOfWeek == DayOfWeek.SUNDAY -> date = date.plusDays(1)
        date.dayOfWeek == DayOfWeek.FRIDAY && date.hour >= 16 -> date = date.plusHours(56)
        date.dayOfWeek == DayOfWeek.MONDAY && date.hour <= 8 -> date = date.withHour(10)
    }

    return date
}
