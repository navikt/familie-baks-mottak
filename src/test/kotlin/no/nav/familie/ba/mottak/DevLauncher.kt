package no.nav.familie.ba.mottak

import no.nav.familie.ba.mottak.config.ApplicationConfig
import org.springframework.boot.builder.SpringApplicationBuilder

object DevLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        val app = SpringApplicationBuilder(ApplicationConfig::class.java)
                .profiles("dev", "mock-dokgen-java")
        app.run(*args)
    }
}