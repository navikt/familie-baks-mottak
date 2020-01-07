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
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import javax.transaction.Transactional

private const val OPPRETTET = "OPPRETTET"
private const val KORRIGERT = "KORRIGERT"
private const val OPPLYSNINGSTYPE_DØDSFALL = "DOEDSFALL_V1"
private const val OPPLYSNINGSTYPE_FØDSEL = "FOEDSEL_V1"

@Service
class LeesahConsumer(val taskRepository: TaskRepository, val hendelsesloggRepository: HendelsesloggRepository) {

    val dødsfallCounter: Counter = Metrics.counter("barnetrygd.dodsfall")
    val leesahFeiletCounter: Counter = Metrics.counter("barnetrygd.hendelse.leesha.feilet")
    val fødselOpprettetCounter: Counter = Metrics.counter("barnetrygd.fodsel.opprettet")
    val fødselKorrigertCounter: Counter = Metrics.counter("barnetrygd.fodsel.korrigert")
    val log: Logger = LoggerFactory.getLogger(LeesahConsumer::class.java)
    val secureLogger = LoggerFactory.getLogger("secureLogger")

    @Value("\${FØDSELSHENDELSE_VENT_PÅ_TPS_MINUTTER:1}")
    lateinit var triggerTidForTps: String

    @KafkaListener(topics = ["aapen-person-pdl-leesah-v1"], id = "personhendelse", idIsGroup = false, containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    fun listen(cr: ConsumerRecord<Int, GenericRecord>) {
        try {
            if (hendelsesloggRepository.existsByHendelseId(cr.value().hentHendelseId())){
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

                        if (cr.value().hentEndringstype() == OPPRETTET) {
                            fødselOpprettetCounter.increment()
                        } else if (cr.value().hentEndringstype() == KORRIGERT) {
                            fødselKorrigertCounter.increment()
                        }

                        val task = Task.nyTaskMedTriggerTid(MottaFødselshendelseTask.TASK_STEP_TYPE,
                                                            cr.value().hentPersonident(),
                                                            LocalDateTime.now().plusMinutes(triggerTidForTps.toLong()))
                        taskRepository.save(task)

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

    private fun GenericRecord.hentHendelseId() =
            get("hendelseId").toString()

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