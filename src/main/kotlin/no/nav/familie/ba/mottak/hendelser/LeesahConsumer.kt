package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.Hendelseslogg
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.task.MottaFødselshendelseTask
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import no.nav.person.pdl.leesah.Endringstype.KORRIGERT
import no.nav.person.pdl.leesah.Endringstype.OPPRETTET
import no.nav.person.pdl.leesah.Personhendelse
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
    val fødselIgnorertUnder18årCounter: Counter = Metrics.counter("barnetrygd.fodsel.ignorertunder18")
    val log: Logger = LoggerFactory.getLogger(LeesahConsumer::class.java)
    val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

    @KafkaListener(topics = ["aapen-person-pdl-leesah-v1"],
                   id = "personhendelse",
                   idIsGroup = false,
                   containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    fun listen(cr: ConsumerRecord<Int, Personhendelse>, ack: Acknowledgment) {
        try {
            val hendelse = cr.value()
            if (hendelsesloggRepository.existsByHendelseIdAndConsumer(hendelse.getHendelseId(), CONSUMER_PDL)) {
                ack.acknowledge()
                return
            }

            if (hendelse.getOpplysningstype() == OPPLYSNINGSTYPE_DØDSFALL) {
                behandleDødsfallHendelser(hendelse, cr)
            } else if (hendelse.getOpplysningstype() == OPPLYSNINGSTYPE_FØDSEL) {
                behandleFødselshendelser(hendelse, cr)
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

    private fun behandleFødselshendelser(hendelse: Personhendelse,
                                         cr: ConsumerRecord<Int, Personhendelse>) {
        when (hendelse.getEndringstype()) {
            OPPRETTET, KORRIGERT -> {
                logHendelse(cr, hendelse, "fødselsdato: ${hendelse.getFoedsel().getFoedselsdato()}")

                if (erUnder6måneder(hendelse.getFoedsel().getFoedselsdato())) {
                    if (hendelse.getEndringstype() == OPPRETTET) {
                        fødselOpprettetCounter.increment()
                    } else if (hendelse.getEndringstype() == KORRIGERT) {
                        fødselKorrigertCounter.increment()
                    }

                    val task = Task.nyTaskMedTriggerTid(MottaFødselshendelseTask.TASK_STEP_TYPE,
                                                        cr.value().hentPersonident(),
                                                        LocalDateTime.now().plusMinutes(triggerTidForTps),
                                                        Properties().apply {
                                                            this["ident"] = cr.value().hentPersonident()
                                                        })
                    taskRepository.save(task)
                } else if (erUnder18år(hendelse.getFoedsel().getFoedselsdato())) {
                    fødselIgnorertUnder18årCounter.increment()
                } else {
                    fødselIgnorertCounter.increment()
                }

            }

            else -> {
                logHendelse(cr, hendelse)
            }
        }
        hendelsesloggRepository.save(Hendelseslogg(cr.offset(),
                                                   hendelse.getHendelseId(),
                                                   CONSUMER_PDL,
                                                   mapOf("aktørId" to hendelse.hentAktørId(),
                                                         "opplysningstype" to hendelse.getOpplysningstype(),
                                                         "endringstype" to hendelse.getEndringstype().name).toProperties()))
    }

    private fun logHendelse(cr: ConsumerRecord<Int, Personhendelse>,
                            hendelse: Personhendelse, ekstraInfo: String = "") {
        log.info("Melding mottatt på topic: {}, partisjon: {}, offset: {}, opplysningstype: {}, aktørid: {}, " +
                 "endringstype: {}, $ekstraInfo",
                 cr.topic(),
                 cr.partition(),
                 cr.offset(),
                 hendelse.getOpplysningstype(),
                 hendelse.hentAktørId(),
                 hendelse.getEndringstype())
    }

    private fun behandleDødsfallHendelser(hendelse: Personhendelse,
                                          cr: ConsumerRecord<Int, Personhendelse>) {
        dødsfallCounter.increment()

        when (hendelse.getEndringstype()) {
            OPPRETTET, KORRIGERT -> {
                logHendelse(cr, hendelse, "dødsdato: ${hendelse.getDoedsfall().getDoedsdato()}")
            }
            else -> {
                logHendelse(cr, hendelse)
            }
        }

        hendelsesloggRepository.save(Hendelseslogg(cr.offset(),
                                                   hendelse.getHendelseId(),
                                                   CONSUMER_PDL,
                                                   mapOf("aktørId" to hendelse.hentAktørId(),
                                                         "opplysningstype" to hendelse.getOpplysningstype(),
                                                         "endringstype" to hendelse.getEndringstype().name).toProperties()))
    }

    private fun erUnder18år(fødselsDato: LocalDate): Boolean {
        return LocalDate.now().isBefore(fødselsDato.plusYears(18))
    }

    private fun erUnder6måneder(fødselsDato: LocalDate): Boolean {
        return LocalDate.now().isBefore(fødselsDato.plusMonths(6))
    }

    // TODO: Skal gjøres tydeligere og mer robust.
    private fun Personhendelse.hentAktørId() =
            this.getPersonidenter().first { it.length == 13 }

    // TODO: Ditto.
    private fun Personhendelse.hentPersonident() =
            this.getPersonidenter().first { it.length == 11 }

    companion object {
        private val CONSUMER_PDL = HendelseConsumer.PDL
    }
}
