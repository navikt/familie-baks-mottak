package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.kontrakter.felles.tilbakekreving.Behandlingsårsakstype
import java.time.LocalDate

data class RestKsSakBehandlingRequest(
    val kategori: String,
    val søkersIdent: String,
    val behandlingÅrsak: Behandlingsårsakstype,
    val saksbehandlerIdent: String,
    val søknadMottattDato: LocalDate,
)
