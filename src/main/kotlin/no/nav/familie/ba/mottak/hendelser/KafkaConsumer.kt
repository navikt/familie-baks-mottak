package no.nav.familie.ba.mottak.hendelser

import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class KafkaConsumer {

    val log = LoggerFactory.getLogger(KafkaConsumer::class.java)

    @KafkaListener(topics = ["aapen-person-pdl-leesah-v1"])
    fun listen(cr: ConsumerRecord<String, GenericRecord>) {
        log.info("Melding mottatt på topic: {}, partisjon: {} med offset: {}, og verdi: {}", cr.topic(), cr.partition(), cr.offset(), cr.value())
    }
}