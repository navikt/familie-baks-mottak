package no.nav.familie.baks.mottak.config.featureToggle

enum class FeatureToggle(
    val navn: String,
) {
    // Operasjonelle
    HOPP_OVER_INFOTRYGD_SJEKK("familie-baks-mottak.hopp-over-infotrygd-sjekk"),

    // Operasjonelle
    AUTOMATISK_JOURNALFØR_ENHET_2103("familie-ba-sak.tillatt-behandling-av-kode6-kode19"),

    // Ny pdf kvittering
    NY_FAMILIE_PDF_KVITTERING("familie-ba-soknad.ny-pdf"),
}
