package no.nav.familie.baks.mottak

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

private const val AVRO_SERIALIZABLE_PACKAGES =
    "no.nav.joarkjournalfoeringhendelser,no.nav.person.pdl.leesah,no.nav.person.identhendelse.v1"

@SpringBootApplication
class Launcher

fun main(args: Array<String>) {
    System.setProperty("org.apache.avro.SERIALIZABLE_PACKAGES", AVRO_SERIALIZABLE_PACKAGES)
    SpringApplication.run(Launcher::class.java, *args)
}
