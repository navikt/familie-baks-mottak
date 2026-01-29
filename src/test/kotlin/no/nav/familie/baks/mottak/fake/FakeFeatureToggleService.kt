package no.nav.familie.baks.mottak.fake

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggle
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleService
import no.nav.familie.unleash.UnleashService

class FakeFeatureToggleService(
    unleashService: UnleashService,
) : FeatureToggleService(unleashService) {
    private val overrides = mutableMapOf<FeatureToggle, Boolean>()

    fun set(
        toggle: FeatureToggle,
        enabled: Boolean,
    ) {
        overrides[toggle] = enabled
    }

    fun reset() {
        overrides.clear()
    }

    override fun isEnabled(
        toggle: FeatureToggle,
    ): Boolean {
        val mockUnleashServiceAnswer = System.getProperty("mockFeatureToggleAnswer")?.toBoolean() ?: true
        return overrides[toggle] ?: mockUnleashServiceAnswer
    }

    override fun isEnabled(
        toggle: FeatureToggle,
        defaultValue: Boolean,
    ): Boolean {
        val mockUnleashServiceAnswer = System.getProperty("mockFeatureToggleAnswer")?.toBoolean() ?: defaultValue
        return overrides[toggle] ?: mockUnleashServiceAnswer
    }
}
