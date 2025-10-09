package no.nav.familie.baks.mottak.config.featureToggle

class FeatureToggleConfig {
    companion object {
        // Operasjonelle
        const val HOPP_OVER_INFOTRYGD_SJEKK = "familie-baks-mottak.hopp-over-infotrygd-sjekk"

        // Operasjonelle
        const val AUTOMATISK_JOURNALFØR_ENHET_2103 = "familie-ba-sak.tillatt-behandling-av-kode6-kode19"

        // Ny pdf kvittering
        const val NY_FAMILIE_PDF_KVITTERING = "familie-ba-soknad.ny-pdf"

        const val SEND_BOSTEDSADRESSE_HENDELSER_TIL_BA_SAK = "familie-baks-mottak.send-bostedsadresse-hendelser-til-ba-sak"

        const val SEND_OPPHOLDSADRESSE_HENDELSER_TIL_BA_SAK = "familie-baks-mottak.send-oppholdsadresse-hendelser-til-ba-sak"
    }
}
