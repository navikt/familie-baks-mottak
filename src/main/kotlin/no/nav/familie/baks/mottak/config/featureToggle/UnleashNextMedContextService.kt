package no.nav.familie.baks.mottak.config.featureToggle

import no.nav.familie.unleash.UnleashService
import org.springframework.stereotype.Service

@Service
class UnleashNextMedContextService(
    private val unleashNext: UnleashService,
) {
    fun isEnabled(toggleId: String): Boolean = unleashNext.isEnabled(toggleId)

    fun isEnabled(
        toggleId: String,
        defaultValue: Boolean,
    ) = unleashNext.isEnabled(toggleId, defaultValue)
}
