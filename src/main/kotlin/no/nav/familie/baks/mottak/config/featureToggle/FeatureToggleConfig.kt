package no.nav.familie.baks.mottak.config.featureToggle

class FeatureToggleConfig {
    companion object {
        // Operasjonelle
        const val AUTOMATISK_JOURNALFØRING_AV_KONTANTSTØTTE_SØKNADER = "familie-baks-mottak.automatisk-journalforing-av-ks-soknad"
        const val AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER = "familie-baks-mottak.automatisk-journalforing-av-ba-soknad"

        // Release
        const val BRUK_NY_DOKGEN_LØSNING = "familie-baks-mottak.bruk-ny-dokgen-losning"
    }
}
