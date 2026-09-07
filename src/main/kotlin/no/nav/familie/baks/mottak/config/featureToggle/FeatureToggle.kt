package no.nav.familie.baks.mottak.config.featureToggle

enum class FeatureToggle(
    val navn: String,
) {
    // Operasjonelle
    HOPP_OVER_INFOTRYGD_SJEKK("familie-baks-mottak.hopp-over-infotrygd-sjekk"),

    // Release
    BRUK_AUTOMATISK_BEHANDLING_ÅRSAK("familie-baks-mottak.bruk-automatisk-behandling-aarsak"),
}
