package no.nav.familie.ba.mottak.hendelser

import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service

@Service
class KafkaConsumer {

    val log = LoggerFactory.getLogger(KafkaConsumer::class.java)

    @KafkaListener(topics = ["aapen-person-pdl-leesah-v1"])
    fun listen(@Payload message : String) {
        log.info("KafkaConsumer lytter på melding {} ", message)
    }
}