package no.nav.familie.baks.mottak.util

import java.time.LocalTime

fun LocalTime.isEqualOrAfter(localTimeToCompare: LocalTime): Boolean = this == localTimeToCompare || this.isAfter(localTimeToCompare)

fun LocalTime.isEqualOrBefore(localTimeToCompare: LocalTime): Boolean = this == localTimeToCompare || this.isBefore(localTimeToCompare)
