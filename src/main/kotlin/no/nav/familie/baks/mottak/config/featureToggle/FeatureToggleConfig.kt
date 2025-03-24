package no.nav.familie.baks.mottak.config.featureToggle

class FeatureToggleConfig {
    companion object {
        // Operasjonelle
        const val HOPP_OVER_INFOTRYGD_SJEKK = "familie-ba-sak.hopp-over-infotrygd-sjekk"

        // Release
        const val AUTOMATISK_JOURNALFØRING_AV_KONTANTSTØTTE_SØKNADER = "familie-baks-mottak.automatisk-journalforing-av-ks-soknad"
        const val AUTOMATISK_JOURNALFØRING_AV_BARNETRYGD_SØKNADER = "familie-baks-mottak.automatisk-journalforing-av-ba-soknad"
        const val SETT_BEHANDLINGSTEMA_OG_BEHANDLINGSTYPE_FOR_KLAGE = "familie-baks-mottak.sett-behandlingstema-og-behandlingstype-for-klage"

        // Ny pdf kvittering
        const val NY_FAMILIE_PDF_KVITTERING = "familie-baks-mottak.ny-pdf"
    }
}
