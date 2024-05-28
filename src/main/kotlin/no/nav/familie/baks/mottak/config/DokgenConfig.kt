package no.nav.familie.baks.mottak.config

import no.nav.familie.baks.dokgen.DokGen
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.integrasjoner.PdfClient
import no.nav.familie.baks.mottak.søknad.GammelPdfService
import no.nav.familie.baks.mottak.søknad.NyPdfService
import no.nav.familie.baks.mottak.søknad.PdfService
import no.nav.familie.baks.mottak.søknad.SøknadSpråkvelgerService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DokgenConfig {
    @Bean
    fun dokgen() = DokGen()

    @Bean
    fun pdfService(
        familieDokumentClient: FamilieDokumentClient,
        søknadSpråkvelgerService: SøknadSpråkvelgerService,
        dokgen: DokGen,
        pdfClient: PdfClient,
        unleashService: UnleashNextMedContextService,
    ): PdfService {
        if (unleashService.isEnabled(FeatureToggleConfig.BRUK_NY_DOKGEN_LØSNING)) {
            return NyPdfService(
                familieDokumentClient,
                søknadSpråkvelgerService,
                dokgen,
            )
        }
        return GammelPdfService(
            pdfClient,
            søknadSpråkvelgerService,
        )
    }
}
