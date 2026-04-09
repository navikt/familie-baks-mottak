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

    HENT_ARBEIDSFORDELING_MED_BEHANDLINGSTYPE("familie-baks-mottak.hent-arbeidsfordeling-med-behandlingstype"),

    SEND_OPPGAVE_OM_ADRESSEBESKYTTELSE_ER_FJERNET("familie-baks-mottak.send-oppgave-om-adressebeskyttelse-er-fjernet"),
}
