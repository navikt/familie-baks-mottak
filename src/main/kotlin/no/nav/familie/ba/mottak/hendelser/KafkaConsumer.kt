package no.nav.familie.ba.mottak.hendelser

import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class KafkaConsumer {

    val log = LoggerFactory.getLogger(KafkaConsumer::class.java)

    @KafkaListener(topics = ["aapen-person-pdl-leesah-v1"], id = "personhendelse", idIsGroup = false, containerFactory = "kafkaListenerContainerFactory")
    fun listen(cr: ConsumerRecord<String, GenericRecord>) {
        log.info("Melding mottatt på topic: {}, partisjon: {} med offset: {}, og verdi: {}", cr.topic(), cr.partition(), cr.offset(), cr.value())

        val hendelse = cr.value()
        val opplysningstype = hendelse.hentOpplysningstype()
        log.info("Opplysningstype: $opplysningstype")
    }

    private fun GenericRecord.hentOpplysningstype() =
            get("opplysningstype").toString()

    private fun GenericRecord.hentAktorId() =
            (get("personidenter") as GenericData.Array<*>)
                    .map { it.toString() }
                    .first{ it.length == 13 }

    private fun GenericRecord.hentEndringstype() =
            get("endringstype").toString()
}