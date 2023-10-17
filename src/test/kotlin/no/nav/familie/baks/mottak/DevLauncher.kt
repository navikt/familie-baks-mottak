package no.nav.familie.baks.mottak

import no.nav.familie.baks.mottak.config.ApplicationConfig
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Import

@Import(ApplicationConfig::class)
@EnableMockOAuth2Server
class DevLauncher

fun main(args: Array<String>) {
    val app =
        SpringApplicationBuilder(DevLauncher::class.java)
            .profiles("dev")
    app.run(*args)
}
