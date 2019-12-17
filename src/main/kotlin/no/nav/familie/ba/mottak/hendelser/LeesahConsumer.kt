package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.task.MottaFødselshendelseTask
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

private const val OPPRETTET = "OPPRETTET"
private const val KORRIGERT = "KORRIGERT"
private const val OPPLYSNINGSTYPE_DØDSFALL = "DOEDSFALL_V1"
private const val OPPLYSNINGSTYPE_FØDSEL = "FOEDSEL_V1"

@Service
class LeesahConsumer(val taskRepository: TaskRepository) {

    val dødsfallCounter = Metrics.counter("barnetrygd.dodsfall")
    val leesahFeiletCounter = Metrics.counter("barnetrygd.hendelse.leesha.feilet")
    val fødselOpprettetCounter = Metrics.counter("barnetrygd.fodsel.opprettet")
    val fødselKorrigertCounter = Metrics.counter("barnetrygd.fodsel.korrigert")
    val log = LoggerFactory.getLogger(LeesahConsumer::class.java)

    @KafkaListener(topics = ["aapen-person-pdl-leesah-v1"], id = "personhendelse", idIsGroup = false, containerFactory = "kafkaListenerContainerFactory")
    fun listen(cr: ConsumerRecord<String, GenericRecord>) {

        try {
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
            } else if (cr.value().erFødsel())
                when (cr.value().hentEndringstype()) {
                    OPPRETTET, KORRIGERT -> {
                        log.info("Melding mottatt på topic: {}, partisjon: {}, offset: {}, opplysningstype: {}, aktørid: {}, endringstype: {}, fødselsdato: {}",
                                cr.topic(), cr.partition(), cr.offset(), cr.value().hentOpplysningstype(), cr.value().hentAktørId(),
                                cr.value().hentEndringstype(), cr.value().hentFødselsdato())

                        if (cr.value().hentEndringstype() == OPPRETTET) {
                            fødselOpprettetCounter.increment()
                        } else if (cr.value().hentEndringstype() == KORRIGERT) {
                            fødselKorrigertCounter.increment()
                        }

                        val task = Task.nyTaskMedTriggerTid(MottaFødselshendelseTask.TASK_STEP_TYPE, cr.value().hentPersonident(), LocalDateTime.now().plusHours(24))
                        taskRepository.save(task)
                    }
                    else -> {
                        log.info("Melding mottatt på topic: {}, partisjon: {}, offset: {}, opplysningstype: {}, aktørid: {}, endringstype: {}",
                                cr.topic(), cr.partition(), cr.offset(), cr.value().hentOpplysningstype(), cr.value().hentAktørId(), cr.value().hentEndringstype())
                    }
                }
        } catch (e: RuntimeException) {
            leesahFeiletCounter.increment()
            log.error("Feil ved konsumering av melding fra aapen-person-pdl-leesah-v1 . id {}, offset: {}, partition: {}",
                      cr.key(),
                      cr.offset(),
                      cr.partition()
            );
            throw e;
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

    private fun GenericRecord.hentPersonident() =
            (get("personidenter") as GenericData.Array<*>)
                    .map { it.toString() }
                    .first { it.length == 11 }

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