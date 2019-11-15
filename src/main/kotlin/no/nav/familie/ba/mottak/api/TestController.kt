package no.nav.familie.ba.mottak.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api/"], produces = [MediaType.APPLICATION_JSON_VALUE])
class PingController(@Value("\${KAFKA_BROKERS}") val kafkaBrokers: String) {

    @GetMapping("/ping")
    fun ping(): String {
        return kafkaBrokers
    }
}