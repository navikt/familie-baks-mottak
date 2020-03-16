package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.domene.HendelseConsumer
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
import java.util.*
import javax.transaction.Transactional

private const val OPPRETTET = "OPPRETTET"
private const val KORRIGERT = "KORRIGERT"
private const val OPPLYSNINGSTYPE_DØDSFALL = "DOEDSFALL_V1"
private const val OPPLYSNINGSTYPE_FØDSEL = "FOEDSEL_V1"

@Service
class LeesahConsumer(val taskRepository: TaskRepository,
                     val hendelsesloggRepository: HendelsesloggRepository,
                     @Value("\${FØDSELSHENDELSE_VENT_PÅ_TPS_MINUTTER}") val triggerTidForTps: Long) {

    val dødsfallCounter: Counter = Metrics.counter("barnetrygd.dodsfall")
    val leesahFeiletCounter: Counter = Metrics.counter("barnetrygd.hendelse.leesha.feilet")
    val fødselOpprettetCounter: Counter = Metrics.counter("barnetrygd.fodsel.opprettet")
    val fødselKorrigertCounter: Counter = Metrics.counter("barnetrygd.fodsel.korrigert")
    val fødselIgnorertCounter: Counter = Metrics.counter("barnetrygd.fodsel.ignorert")
    val fødselIgnorertUnder18årCounter: Counter = Metrics.counter("barnetrygd.fodsel.ignorert.under18")
    val log: Logger = LoggerFactory.getLogger(LeesahConsumer::class.java)
    val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

    @KafkaListener(topics = ["aapen-person-pdl-leesah-v1"],
                   id = "personhendelse",
                   idIsGroup = false,
                   containerFactory = "kafkaLeesahListenerContainerFactory")
    @Transactional
    fun listen(cr: ConsumerRecord<Int, GenericRecord>, ack: Acknowledgment) {
        cr.value().schema
        try {
            if (hendelsesloggRepository.existsByHendelseIdAndConsumer(cr.value().hentHendelseId(), CONSUMER_PDL)) {
                ack.acknowledge()
                return
            }
            SECURE_LOGGER.info("Har mottatt leesah-hendelse: schema:${cr.value().schema} record:$cr")
            if (cr.value().erDødsfall()) {
                behandleDødsfallHendelse(cr)
            } else if (cr.value().erFødsel()) {
                behandleFødselsHendelse(cr)
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

    private fun behandleFødselsHendelse(cr: ConsumerRecord<Int, GenericRecord>) {
        when (cr.value().hentEndringstype()) {
            OPPRETTET, KORRIGERT -> {
                logHendelse(cr, "fødselsdato: ${cr.value().hentFødselsdato()}")

                if (erUnder6mnd(cr.value().hentFødselsdato())) {
                    if (cr.value().hentEndringstype() == OPPRETTET) {
                        fødselOpprettetCounter.increment()
                    } else if (cr.value().hentEndringstype() == KORRIGERT) {
                        fødselKorrigertCounter.increment()
                    }

                    val task = Task.nyTaskMedTriggerTid(MottaFødselshendelseTask.TASK_STEP_TYPE,
                                                        cr.value().hentPersonident(),
                                                        LocalDateTime.now().plusMinutes(triggerTidForTps),
                                                        Properties().apply {
                                                            this["ident"] = cr.value().hentPersonident()
                                                        })
                    taskRepository.save(task)
                } else if (erUnder18år(cr.value().hentFødselsdato())) {
                    fødselIgnorertUnder18årCounter.increment()
                } else {
                    fødselIgnorertCounter.increment()
                }

            }

            else -> {
                logHendelse(cr)
            }
        }
        oppdaterHendelslogg(cr)
    }

    private fun behandleDødsfallHendelse(cr: ConsumerRecord<Int, GenericRecord>) {
        dødsfallCounter.increment()

        when (cr.value().hentEndringstype()) {
            OPPRETTET, KORRIGERT -> {
                logHendelse(cr, "dødsdato: ${cr.value().hentDødsdato()}")
            }
            else -> {
                logHendelse(cr)
            }
        }
        oppdaterHendelslogg(cr)
    }

    private fun oppdaterHendelslogg(cr: ConsumerRecord<Int, GenericRecord>) {
        hendelsesloggRepository.save(Hendelseslogg(cr.offset(),
                                                   cr.value().hentHendelseId(),
                                                   CONSUMER_PDL,
                                                   mapOf("aktørId" to cr.value().hentAktørId(),
                                                         "opplysningstype" to cr.value().hentOpplysningstype(),
                                                         "endringstype" to cr.value().hentEndringstype()).toProperties()))
    }

    private fun logHendelse(cr: ConsumerRecord<Int, GenericRecord>, ekstraInfo: String = "") {
        log.info("Melding mottatt på topic: {}, partisjon: {}, offset: {}, opplysningstype: {}, aktørid: {}, " +
                 "endringstype: {}, $ekstraInfo",
                 cr.topic(),
                 cr.partition(),
                 cr.offset(),
                 cr.value().hentOpplysningstype(),
                 cr.value().hentAktørId(),
                 cr.value().hentEndringstype())
    }

    private fun erUnder18år(fødselsDato: LocalDate): Boolean {
        return LocalDate.now().isBefore(fødselsDato.plusYears(18))
    }

    private fun erUnder6mnd(fødselsDato: LocalDate): Boolean {
        return LocalDate.now().isBefore(fødselsDato.plusMonths(6))
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

    companion object {
        private val CONSUMER_PDL = HendelseConsumer.PDL
        val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
    }
}