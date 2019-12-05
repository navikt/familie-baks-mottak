package no.nav.familie.ba.mottak

import no.nav.familie.ba.mottak.config.ApplicationConfig
import org.springframework.boot.builder.SpringApplicationBuilder

class DevLauncherPostgres
    fun main(args: Array<String>) {
        SpringApplicationBuilder(ApplicationConfig::class.java).profiles("postgres").run(*args)
    }