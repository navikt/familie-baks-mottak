package no.nav.familie.ba.mottak

import no.nav.familie.ba.mottak.config.ApplicationConfig
import no.nav.security.token.support.test.spring.TokenGeneratorConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Import

@Import(TokenGeneratorConfiguration::class)
class DevLauncherPostgres
    fun main(args: Array<String>) {
        SpringApplicationBuilder(ApplicationConfig::class.java).profiles("postgres").run(*args)
    }