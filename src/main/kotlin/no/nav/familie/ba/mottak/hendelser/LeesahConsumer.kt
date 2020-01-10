package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.domene.Hendelseslogg
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.task.MottaFødselshendelseTask
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import javax.transaction.Transactional

private const val OPPRETTET = "OPPRETTET"
private const val KORRIGERT = "KORRIGERT"
private const val OPPLYSNINGSTYPE_DØDSFALL = "DOEDSFALL_V1"
private const val OPPLYSNINGSTYPE_FØDSEL = "FOEDSEL_V1"

@Service
class LeesahConsumer(val taskRepository: TaskRepository,
                     val hendelsesloggRepository: HendelsesloggRepository,
                     @Value("\${FØDSELSHENDELSE_VENT_PÅ_TPS_MINUTTER}") val triggerTidForTps: Long
) {
    val dødsfallCounter: Counter = Metrics.counter("barnetrygd.dodsfall")
    val leesahFeiletCounter: Counter = Metrics.counter("barnetrygd.hendelse.leesha.feilet")
    val fødselOpprettetCounter: Counter = Metrics.counter("barnetrygd.fodsel.opprettet")
    val fødselKorrigertCounter: Counter = Metrics.counter("barnetrygd.fodsel.korrigert")
    val fødselIgnorertCounter: Counter = Metrics.counter("barnetrygd.fodsel.ignorert")
    val log: Logger = LoggerFactory.getLogger(LeesahConsumer::class.java)
    val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

    @KafkaListener(topics = ["aapen-person-pdl-leesah-v1"], id = "personhendelse", idIsGroup = false, containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    fun listen(cr: ConsumerRecord<Int, GenericRecord>, ack: Acknowledgment) {
        try {
            if (hendelsesloggRepository.existsByHendelseId(cr.value().hentHendelseId())){
                ack.acknowledge()
                return
            }
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
                hendelsesloggRepository.save(Hendelseslogg(cr.offset(),cr.value().hentHendelseId(),cr.value().hentAktørId(),cr.value().hentOpplysningstype(),cr.value().hentEndringstype()))
            } else if (cr.value().erFødsel()) {
                when (cr.value().hentEndringstype()) {
                    OPPRETTET, KORRIGERT -> {
                        log.info("Melding mottatt på topic: {}, partisjon: {}, offset: {}, opplysningstype: {}, aktørid: {}, endringstype: {}, fødselsdato: {}",
                                 cr.topic(),
                                 cr.partition(),
                                 cr.offset(),
                                 cr.value().hentOpplysningstype(),
                                 cr.value().hentAktørId(),
                                 cr.value().hentEndringstype(),
                                 cr.value().hentFødselsdato())

                        if(erUnder18År(cr.value().hentFødselsdato())) {
                            if (cr.value().hentEndringstype() == OPPRETTET) {
                                fødselOpprettetCounter.increment()
                            } else if (cr.value().hentEndringstype() == KORRIGERT) {
                                fødselKorrigertCounter.increment()
                            }

                            val task = Task.nyTaskMedTriggerTid(MottaFødselshendelseTask.TASK_STEP_TYPE,
                                                                cr.value().hentPersonident(),
                                                                LocalDateTime.now().plusMinutes(triggerTidForTps))
                            taskRepository.save(task)
                        } else {
                            fødselIgnorertCounter.increment()
                        }

                    }

                    else -> {
                        log.info("Melding mottatt på topic: {}, partisjon: {}, offset: {}, opplysningstype: {}, aktørid: {}, endringstype: {}",
                                 cr.topic(),
                                 cr.partition(),
                                 cr.key(),
                                 cr.offset(),
                                 cr.value().hentOpplysningstype(),
                                 cr.value().hentAktørId(),
                                 cr.value().hentEndringstype())
                    }
                }
                hendelsesloggRepository.save(Hendelseslogg(cr.offset(),cr.value().hentHendelseId(),cr.value().hentAktørId(),cr.value().hentOpplysningstype(),cr.value().hentEndringstype()))
            }
        } catch (e: RuntimeException) {
            leesahFeiletCounter.increment()
            log.error("Feil ved konsumering av melding fra aapen-person-pdl-leesah-v1 . id {}, offset: {}, partition: {}",
                      cr.key(),
                      cr.offset(),
                      cr.partition()
            )
            secureLogger.error("Feil i prosessering av leesah-hendelser", e)
            throw RuntimeException("Feil i prosessering av leesah-hendelser")
        }

        ack.acknowledge()
    }

    private fun erUnder18År(fødselsDato: LocalDate): Boolean {
        return Period.between(
                fødselsDato,
                LocalDate.now()
        ).getYears() < 18
    }

    private fun GenericRecord.erDødsfall() =
            get("opplysningstype").toString() == OPPLYSNINGSTYPE_DØDSFALL

    private fun GenericRecord.erFødsel() =
            get("opplysningstype").toString() == OPPLYSNINGSTYPE_FØDSEL

    private fun GenericRecord.hentOpplysningstype() =
            get("opplysningstype").toString()

    // TODO: Skal gjøres tydeligere og mer robust.
    private fun GenericRecord.hentAktørId() =
            (get("personidenter") as GenericData.Array<*>)
                    .map { it.toString() }
                    .first { it.length == 13 }

    // TODO: Ditto.
    private fun GenericRecord.hentPersonident() =
            (get("personidenter") as GenericData.Array<*>)
                    .map { it.toString() }
                    .first { it.length == 11 }

    private fun GenericRecord.hentEndringstype() =
            get("endringstype").toString()

    private fun GenericRecord.hentHendelseId() =
            get("hendelseId").toString()

    private fun GenericRecord.hentDødsdato(): LocalDate {
        return try {
            val dato = (get("doedsfall") as GenericRecord?)?.get("doedsdato")

            // Integrasjonstester bruker EmbeddedKafka, der en datoverdi tolkes direkte som en LocalDate.
            // I prod tolkes datoer som en Integer.
            if (dato is LocalDate) {
                dato
            } else {
                LocalDate.ofEpochDay((dato as Int).toLong())
            }

        } catch (exception: Exception) {
            log.error("Deserialisering av dødsdato feiler")
            throw exception
        }
    }

    private fun GenericRecord.hentFødselsdato(): LocalDate {
        return try {
            val dato = (get("foedsel") as GenericRecord?)?.get("foedselsdato")

            if (dato is LocalDate) {
                dato
            } else {
                LocalDate.ofEpochDay((dato as Int).toLong())
            }
        } catch (exception: Exception) {
            log.error("Deserialisering av fødselsdato feiler")
            throw exception
        }
    }
}