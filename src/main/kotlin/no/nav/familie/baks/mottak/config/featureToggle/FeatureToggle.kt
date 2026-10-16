package no.nav.familie.baks.mottak.config.featureToggle

enum class FeatureToggle(
    val navn: String,
) {
    // Operasjonelle
    HOPP_OVER_INFOTRYGD_SJEKK("familie-baks-mottak.hopp-over-infotrygd-sjekk"),

    // Release
    BRUK_AUTOMATISK_BEHANDLING_ÅRSAK("familie-baks-mottak.bruk-automatisk-behandling-aarsak"),
    SKAL_SENDE_STATISTIKK_AUTOMATISK_BEHANDLING_SØKNAD("familie-baks-mottak.skal-sende-statistikk-automatisk-behandling-soknad"),
}
