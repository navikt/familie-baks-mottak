package no.nav.familie.baks.mottak.integrasjoner

data class AutovedtakSøknadStatistikkDto(
    val journalpostId: Long,
    val feilårsaker: List<AutovedtakSøknadFeilårsak>,
)

enum class AutovedtakSøknadFeilårsak {
    SØKER_HAR_LØPENDE_BARNETRYGD,
    SØKER_HAR_HATT_BARNETRYGD_TIDLIGERE,
    SØKNAD_MANGLER_VEDLEGG,
}
