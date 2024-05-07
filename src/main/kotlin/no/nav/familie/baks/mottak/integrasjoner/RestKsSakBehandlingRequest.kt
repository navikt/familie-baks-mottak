package no.nav.familie.baks.mottak.integrasjoner

import java.time.LocalDateTime

data class RestKsSakBehandlingRequest(
    val kategori: String,
    val søkersIdent: String,
    val behandlingÅrsak: String,
    val søknadMottattDato: LocalDateTime,
)
