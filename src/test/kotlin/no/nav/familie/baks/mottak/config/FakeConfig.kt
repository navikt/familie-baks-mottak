package no.nav.familie.baks.mottak.config

import no.nav.familie.baks.mottak.fake.FakeFeatureToggleService
import no.nav.familie.unleash.UnleashService
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class FakeConfig {
    @Bean
    @Primary
    fun fakeFeatureToggleService(
        unleashService: UnleashService,
    ): FakeFeatureToggleService = FakeFeatureToggleService(unleashService)
}
