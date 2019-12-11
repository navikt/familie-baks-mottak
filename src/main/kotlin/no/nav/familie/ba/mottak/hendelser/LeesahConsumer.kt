package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Metrics
import no.nav.familie.prosessering.domene.TaskRepository
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.time.LocalDate

private const val OPPRETTET = "OPPRETTET"
private const val KORRIGERT = "KORRIGERT"
private const val OPPLYSNINGSTYPE_DØDSFALL = "DOEDSFALL_V1"
private const val OPPLYSNINGSTYPE_FØDSEL = "FOEDSEL_V1"

@Service
class LeesahConsumer(val taskRepository: TaskRepository) {

    val dødsfallCounter = Metrics.counter("barnetrygd.dodsfall")
    val fødselCounter = Metrics.counter("barnetrygd.fodsel")
    val log = LoggerFactory.getLogger(LeesahConsumer::class.java)

    @KafkaListener(topics = ["aapen-person-pdl-leesah-v1"], id = "personhendelse", idIsGroup = false, containerFactory = "kafkaListenerContainerFactory")
    fun listen(cr: ConsumerRecord<String, GenericRecord>) {
        if (cr.value().erDødsfall()) {
            dødsfallCounter.increment()

            when (cr.value().hentEndringstype()) {
                OPPRETTET, KORRIGERT -> {
                    log.info("Melding mottatt på topic: {}, partisjon: {}, offset: {}, opplysningstype: {}, aktørid: {}, endringstype: {}, dødsdato: {}",
                            cr.topic(), cr.partition(), cr.offset(), cr.value().hentOpplysningstype(), cr.value().hentAktørId(),
                            cr.value().hentEndringstype(), cr.value().hentDødsdato())
                }
                else -> {
                    log.info("Melding mottatt på topic: {}, partisjon: {}, offset: {}, opplysningstype: {}, aktørid: {}, endringstype: {}",
                            cr.topic(), cr.partition(), cr.offset(), cr.value().hentOpplysningstype(), cr.value().hentAktørId(), cr.value().hentEndringstype())
                }
            }
        } else if (cr.value().erFødsel()) {
            fødselCounter.increment()

            when (cr.value().hentEndringstype()) {
                OPPRETTET, KORRIGERT -> {
                    log.info("Melding mottatt på topic: {}, partisjon: {}, offset: {}, opplysningstype: {}, aktørid: {}, endringstype: {}, fødselsdato: {}",
                            cr.topic(), cr.partition(), cr.offset(), cr.value().hentOpplysningstype(), cr.value().hentAktørId(),
                            cr.value().hentEndringstype(), cr.value().hentFødselsdato())
                }
                else -> {
                    log.info("Melding mottatt på topic: {}, partisjon: {}, offset: {}, opplysningstype: {}, aktørid: {}, endringstype: {}",
                            cr.topic(), cr.partition(), cr.offset(), cr.value().hentOpplysningstype(), cr.value().hentAktørId(), cr.value().hentEndringstype())
                }
            }
        }
    }

    private fun GenericRecord.erDødsfall() =
            get("opplysningstype").toString() == OPPLYSNINGSTYPE_DØDSFALL

    private fun GenericRecord.erFødsel() =
            get("opplysningstype").toString() == OPPLYSNINGSTYPE_FØDSEL

    private fun GenericRecord.hentOpplysningstype() =
            get("opplysningstype").toString()

    private fun GenericRecord.hentAktørId() =
            (get("personidenter") as GenericData.Array<*>)
                    .map { it.toString() }
                    .first { it.length == 13 }

    private fun GenericRecord.hentEndringstype() =
            get("endringstype").toString()

    private fun GenericRecord.hentDødsdato(): LocalDate {
        try {
            return LocalDate.ofEpochDay((get("doedsfall") as GenericRecord?)?.get("doedsdato").toString().toLong())
        } catch (exception: Exception) {
            log.error("Deserialisering av dødsdato feiler")
            throw exception
        }
    }

    private fun GenericRecord.hentFødselsdato(): LocalDate {
        try {
            return LocalDate.ofEpochDay((get("foedsel") as GenericRecord?)?.get("foedselsdato").toString().toLong())
        } catch (exception: Exception) {
            log.error("Deserialisering av fødselsdato feiler")
            throw exception
        }
    }
}