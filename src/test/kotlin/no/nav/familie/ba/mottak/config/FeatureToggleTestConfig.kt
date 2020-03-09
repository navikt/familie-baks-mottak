package no.nav.familie.ba.mottak.config

import io.mockk.every
import io.mockk.mockk
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary


@Configuration
class FeatureToggleTestConfig {

    @Bean
    @Primary
    fun mockFeatureToogleService(): FeatureToggleService {
        val featureToggleService = mockk<FeatureToggleService>(relaxed = true)

        return featureToggleService
    }
}