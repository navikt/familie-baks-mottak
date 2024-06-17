package no.nav.familie.baks.mottak.integrasjoner

import java.time.LocalDateTime

data class Journalpost(
    val journalpostId: String,
    val journalposttype: Journalposttype,
    val journalstatus: Journalstatus,
    val tema: String? = null,
    val behandlingstema: String? = null,
    val sak: Sak? = null,
    val bruker: Bruker? = null,
    val journalforendeEnhet: String? = null,
    val kanal: String? = null,
    val dokumenter: List<DokumentInfo>? = null,
    val datoMottatt: LocalDateTime? = null,
) {
    fun hentHovedDokumentTittel(): String? {
        if (dokumenter.isNullOrEmpty()) error("Journalpost $journalpostId mangler dokumenter")
        return dokumenter.firstOrNull { it.brevkode != null }?.tittel
    }
}

fun Journalpost.erKontantstøtteSøknad(): Boolean = dokumenter?.any { it.brevkode == "NAV 34-00.08" } ?: false

fun Journalpost.erBarnetrygdOrdinærSøknad(): Boolean = dokumenter?.any { it.brevkode == "NAV 33-00.07" } ?: false

fun Journalpost.erBarnetrygdUtvidetSøknad(): Boolean = dokumenter?.any { it.brevkode == "NAV 33-00.09" } ?: false

fun Journalpost.erBarnetrygdSøknad(): Boolean = erBarnetrygdOrdinærSøknad() || erBarnetrygdUtvidetSøknad()

data class Sak(
    val arkivsaksnummer: String?,
    var arkivsaksystem: String?,
    val fagsakId: String?,
    val fagsaksystem: String?,
)

data class Bruker(
    val id: String,
    val type: BrukerIdType,
)

data class DokumentInfo(
    val tittel: String?,
    val brevkode: String?,
    val dokumentstatus: Dokumentstatus?,
    val dokumentvarianter: List<Dokumentvariant>?,
)

data class Dokumentvariant(
    val variantformat: String,
)

enum class Journalposttype {
    I,
    U,
    N,
}

enum class Journalstatus {
    MOTTATT,
    JOURNALFOERT,
    FERDIGSTILT,
    EKSPEDERT,
    UNDER_ARBEID,
    FEILREGISTRERT,
    UTGAAR,
    AVBRUTT,
    UKJENT_BRUKER,
    RESERVERT,
    OPPLASTING_DOKUMENT,
    UKJENT,
}

enum class Dokumentstatus {
    FERDIGSTILT,
    AVBRUTT,
    UNDER_REDIGERING,
    KASSERT,
}

enum class BrukerIdType {
    AKTOERID,
    FNR,
    ORGNR,
}
