package no.nav.familie.baks.mottak.config.featureToggle

enum class FeatureToggle(
    val navn: String,
) {
    // Release
    BRUK_FAMILIE_INTEGRASJONER_I_GCP("familie-baks-mottak.bruk-familie-integrasjoner-i-gcp"),
    BRUK_AUTOMATISK_BEHANDLING_ÅRSAK("familie-baks-mottak.bruk-automatisk-behandling-aarsak"),
}
