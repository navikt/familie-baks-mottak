package no.nav.familie.baks.mottak.integrasjoner

import java.time.LocalDateTime

data class RestOpprettBehandlingRequest(
    val kategori: String,
    val søkersIdent: String,
    val behandlingÅrsak: String,
    val søknadMottattDato: LocalDateTime,
    val behandlingType: BehandlingType,
)

data class RestPersonIdent(
    val personIdent: String,
)

data class RestFagsakIdOgTilknyttetAktørId(
    val aktørId: String,
    val fagsakId: Long,
)

data class RestMinimalFagsak(
    val id: Long,
    val behandlinger: List<RestVisningBehandling>,
    val status: FagsakStatus,
)

class RestVisningBehandling(
    val behandlingId: Long,
    val opprettetTidspunkt: LocalDateTime,
    val kategori: BehandlingKategori,
    val underkategori: BehandlingUnderkategori?,
    val aktiv: Boolean,
    val årsak: String? = null,
    val type: BehandlingType,
    val status: BehandlingStatus,
    val resultat: String? = null,
    val vedtaksdato: LocalDateTime? = null,
)

data class RestFagsak(
    val id: Long,
    val behandlinger: List<RestUtvidetBehandling>,
)

data class RestUtvidetBehandling(
    val aktiv: Boolean,
    val arbeidsfordelingPåBehandling: RestArbeidsfordelingPåBehandling,
    val behandlingId: Long,
    val kategori: BehandlingKategori,
    val opprettetTidspunkt: LocalDateTime,
    val resultat: String,
    val steg: String,
    val type: String,
    val underkategori: BehandlingUnderkategori,
)

data class RestArbeidsfordelingPåBehandling(
    val behandlendeEnhetId: String,
)

enum class BehandlingKategori {
    EØS,
    NASJONAL,
}

enum class BehandlingUnderkategori {
    UTVIDET,
    ORDINÆR,
}

data class RestSøkParam(
    val personIdent: String,
    val barnasIdenter: List<String> = emptyList(),
)

data class RestFagsakDeltager(
    val ident: String,
    val rolle: FagsakDeltagerRolle,
    val fagsakId: Long,
    val fagsakStatus: FagsakStatus,
)

data class RestAnnullerFødsel(val barnasIdenter: List<String>, val tidligereHendelseId: String)

enum class FagsakDeltagerRolle {
    BARN,
    FORELDER,
    UKJENT,
}

enum class FagsakStatus {
    OPPRETTET,
    LØPENDE,
    AVSLUTTET,
}

enum class BehandlingType {
    FØRSTEGANGSBEHANDLING,
    REVURDERING,
    MIGRERING_FRA_INFOTRYGD,
    MIGRERING_FRA_INFOTRYGD_OPPHØRT,
    TEKNISK_ENDRING,
}

enum class BehandlingStatus {
    UTREDES,
    SATT_PÅ_VENT,
    SATT_PÅ_MASKINELL_VENT,
    FATTER_VEDTAK,
    IVERKSETTER_VEDTAK,
    AVSLUTTET,
}

fun RestMinimalFagsak.finnesÅpenBehandlingPåFagsak() = behandlinger.any { it.aktiv && it.status != BehandlingStatus.AVSLUTTET }
