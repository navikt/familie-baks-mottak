package no.nav.familie.baks.mottak.domene

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotNull

data class FeltMap(
    @field:NotNull(message = "Label kan ikke være null")
    val label: String,
    @field:NotNull(message = "Verdiliste kan ikke være null")
    val verdiliste: List<VerdilisteElement>,
    val pdfConfig: PdfConfig = PdfConfig(harInnholdsfortegnelse = true, "nb"),
    val skjemanummer: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class VerdilisteElement(
    val label: String,
    val visningsVariant: String? = null,
    val verdi: String? = null,
    val verdiliste: List<VerdilisteElement>? = null,
    val alternativer: String? = null,
)

data class PdfConfig(
    val harInnholdsfortegnelse: Boolean,
    val språk: String,
)
