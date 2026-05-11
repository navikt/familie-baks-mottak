package no.nav.familie.baks.mottak

import no.nav.familie.baks.mottak.config.ApplicationConfig
import no.nav.familie.baks.mottak.config.MockOAuth2ServerInitializer
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Import

@Import(ApplicationConfig::class)
class DevLauncher

fun main(args: Array<String>) {
    val app =
        SpringApplicationBuilder(DevLauncher::class.java)
            .initializers(MockOAuth2ServerInitializer())
            .profiles("dev")
    app.run(*args)
}
