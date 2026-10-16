package no.nav.familie.baks.mottak.integrasjoner

data class AutomatiskBehandlingSøknadStatistikkDto(
    val journalpostId: Long,
    val feilårsaker: List<AutomatiskBehandlingSøknadFeilårsak>,
)

enum class AutomatiskBehandlingSøknadFeilårsak {
    SØKER_HAR_LØPENDE_BARNETRYGD,
    SØKER_HAR_HATT_BARNETRYGD_TIDLIGERE,
    SØKNAD_MANGLER_VEDLEGG,
}
