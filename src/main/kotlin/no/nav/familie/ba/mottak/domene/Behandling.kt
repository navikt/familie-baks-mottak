package no.nav.familie.ba.mottak.domene

data class NyBehandling(val fødselsnummer: String, val barnasFødselsnummer: Array<String>, val behandlingType: BehandlingType, val journalpostID: String?)

enum class BehandlingType {
    FØRSTEGANGSBEHANDLING,
    REVURDERING,
    KLAGE,
}