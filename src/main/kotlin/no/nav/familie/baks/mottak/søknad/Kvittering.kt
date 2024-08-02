package no.nav.familie.baks.mottak.søknad

import java.time.LocalDateTime

data class Kvittering(
    val tekst: String,
    val mottattDato: LocalDateTime,
)
