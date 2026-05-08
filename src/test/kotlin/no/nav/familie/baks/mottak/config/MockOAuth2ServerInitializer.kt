package no.nav.familie.baks.mottak.config

import no.nav.security.mock.oauth2.MockOAuth2Server
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource

class MockOAuth2ServerInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    companion object {
        private val server: MockOAuth2Server by lazy {
            MockOAuth2Server().also { server ->
                server.start()
                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        server.shutdown()
                    },
                )
            }
        }
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val properties =
            mapOf<String, Any>(
                "TOKEN_X_ISSUER" to server.issuerUrl("tokenx").toString(),
                "TOKEN_X_CLIENT_ID" to "aud-localhost",
                "AZURE_OPENID_CONFIG_ISSUER" to server.issuerUrl("azuread").toString(),
                "AZURE_APP_CLIENT_ID" to "aud-localhost",
            )

        applicationContext.environment.propertySources.addFirst(
            MapPropertySource("mockOAuth2Server", properties),
        )

        applicationContext.beanFactory.registerSingleton("mockOAuth2Server", server)
    }
}
