package no.nav.familie.baks.mottak.integrasjoner

data class AutomatiskBehandlingSøknadStatistikkDto(
    val journalpostId: Long,
    val feilårsaker: List<AutomatiskBehandlingSøknadFeilårsak>,
)

enum class AutomatiskBehandlingSøknadFeilårsak {
    FAGSAK_ER_LØPENDE,
    SØKER_HAR_HATT_INNVILGET_BARNETRYGD,
    SØKNAD_MANGLER_VEDLEGG,
}
