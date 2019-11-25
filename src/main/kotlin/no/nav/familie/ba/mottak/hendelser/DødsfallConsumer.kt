package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Metrics
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.time.LocalDate

private const val OPPLYSNINGSTYPE_DODSFALL = "DOEDSFALL_V1"

@Service
class DødsfallConsumer {

    val dødsfallCounter = Metrics.counter("barnetrygd.dodsfall")
    val log = LoggerFactory.getLogger(DødsfallConsumer::class.java)

    @KafkaListener(topics = ["aapen-person-pdl-leesah-v1"], id = "personhendelse", idIsGroup = false, containerFactory = "kafkaListenerContainerFactory")
    fun listen(cr: ConsumerRecord<String, GenericRecord>) {
        if (cr.value().erDodsfall()) {
            dødsfallCounter.increment()
        }
        log.info("Melding mottatt på topic: {}, partisjon: {} med offset: {}", cr.topic(), cr.partition(), cr.offset())
        log.info("Opplysningstype: {}, Aktørid: {}, Endringstype: {}, Dødsdato: {}", cr.value().hentOpplysningstype(),
                cr.value().hentAktorId(), cr.value().hentEndringstype(), cr.value().hentDodsdato())
    }

    private fun GenericRecord.erDodsfall() =
            get("opplysningstype").toString() == OPPLYSNINGSTYPE_DODSFALL

    private fun GenericRecord.hentOpplysningstype() =
            get("opplysningstype").toString()

    private fun GenericRecord.hentAktorId() =
            (get("personidenter") as GenericData.Array<*>)
                    .map { it.toString() }
                    .first{ it.length == 13 }

    private fun GenericRecord.hentEndringstype() =
            get("endringstype").toString()

    private fun GenericRecord.hentDodsdato(): LocalDate {
        try {
            return LocalDate.ofEpochDay((get("doedsfall") as GenericRecord?)?.get("doedsdato").toString().toLong())
        } catch (exception: Exception) {
            log.error("Deserialisering av dødsdato feiler")
            throw exception
        }
    }
}