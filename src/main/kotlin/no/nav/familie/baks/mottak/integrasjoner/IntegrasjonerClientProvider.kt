package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggle
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

@Component
class IntegrasjonerClientProvider(
    @param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonFssUri: URI,
    @param:Qualifier("integrasjonerRestClient") private val restClientFss: RestClient,
    @param:Value("\${FAMILIE_INTEGRASJONER_GCP_API_URL}") private val integrasjonGcpUri: URI,
    @param:Qualifier("integrasjonerGcpRestClient") private val restClientGcp: RestClient,
    private val featureToggleService: FeatureToggleService,
) {
    fun hentKlient(): Pair<URI, RestClient> =
        if (featureToggleService.isEnabled(FeatureToggle.BRUK_FAMILIE_INTEGRASJONER_I_GCP, defaultValue = false)) {
            integrasjonGcpUri to restClientGcp
        } else {
            integrasjonFssUri to restClientFss
        }
}
