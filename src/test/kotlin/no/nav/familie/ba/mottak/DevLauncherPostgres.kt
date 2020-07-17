package no.nav.familie.ba.mottak

import no.nav.familie.ba.mottak.config.ApplicationConfig
import no.nav.security.token.support.test.spring.TokenGeneratorConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Import

@Import(TokenGeneratorConfiguration::class, ApplicationConfig::class)

class DevLauncherPostgres

fun main(args: Array<String>) {
    val app = SpringApplicationBuilder(DevLauncherPostgres::class.java)
            .profiles("postgres")
    app.run(*args)
}