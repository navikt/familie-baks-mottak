package no.nav.familie.baks.mottak.config.featureToggle

import no.nav.familie.unleash.UnleashService
import org.springframework.stereotype.Service

@Service
class UnleashNextMedContextService(
    private val unleashService: UnleashService,
) {
    fun isEnabled(toggleId: String): Boolean = unleashService.isEnabled(toggleId)
}
