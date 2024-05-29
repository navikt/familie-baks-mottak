package no.nav.familie.baks.mottak.config

import no.nav.familie.baks.dokgen.DokGen
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.DokgenPdfClient
import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentPdfClient
import no.nav.familie.baks.mottak.integrasjoner.PdfClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestOperations

@Configuration
class DokgenConfig {
    @Bean
    fun dokgen() = DokGen()

    @Bean
    fun pdfClient(
        @Qualifier("restTemplateUnsecured") operations: RestOperations,
        @Value("\${FAMILIE_BAKS_DOKGEN_API_URL}") dokgenUri: String,
        familieDokumentClient: FamilieDokumentClient,
        dokGen: DokGen,
        unleashService: UnleashNextMedContextService,
    ): PdfClient {
        if (unleashService.isEnabled(FeatureToggleConfig.BRUK_NY_DOKGEN_LØSNING)) {
            return DokgenPdfClient(
                operations,
                dokgenUri,
            )
        }
        return FamilieDokumentPdfClient(
            familieDokumentClient,
            dokGen,
        )
    }
}
