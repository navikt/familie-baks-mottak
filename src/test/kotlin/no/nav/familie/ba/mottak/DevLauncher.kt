package no.nav.familie.ba.mottak

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.SpringApplication



@SpringBootApplication(scanBasePackages = ["no.nav.familie.ba.mottak"])
class DevLauncher

fun main(args: Array<String>) {
    val springApp = SpringApplication(DevLauncher::class.java)
    springApp.setAdditionalProfiles("dev")
    springApp.run(*args)
}