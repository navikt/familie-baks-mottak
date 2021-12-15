package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.task.SendIdenthendelseTilSakTask
import no.nav.familie.kontrakter.felles.PersonIdent
import no.nav.familie.prosessering.domene.TaskRepository
import no.nav.person.pdl.aktor.v2.Aktor
import no.nav.person.pdl.aktor.v2.Type
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import javax.transaction.Transactional

@Service
@ConditionalOnProperty(
    value = ["funksjonsbrytere.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class IdenthendelseConsumer(private val taskRepository: TaskRepository) {

    val identhendelseFeiletCounter: Counter = Metrics.counter("barnetrygd.hendelse.ident.feilet")

    @KafkaListener(
        topics = ["aapen-person-pdl-aktor-v1"],
        id = "identhendelse",
        idIsGroup = false,
        containerFactory = "kafkaIdenthendelseListenerContainerFactory"
    )
    @Transactional
    fun listen(consumerRecord: ConsumerRecord<String, Aktor>, ack: Acknowledgment) {
        val aktør = consumerRecord.value()
        val folkeregisterident = aktør.identifikatorer.single { it.type == Type.FOLKEREGISTERIDENT && it.gjeldende }

        try {
            SECURE_LOGGER.info("Har mottatt ident-hendelse $consumerRecord")
            if (opprettTask) {
                val task =
                    SendIdenthendelseTilSakTask.opprettTask(ident = PersonIdent(ident = folkeregisterident.idnummer.toString()))
                taskRepository.save(task)
            }
        } catch (e: RuntimeException) {
            identhendelseFeiletCounter.increment()
            SECURE_LOGGER.error("Feil i prosessering av ident-hendelser", e)
            throw RuntimeException("Feil i prosessering av ident-hendelser")
        }

        ack.acknowledge()
    }

    companion object {

        val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
        val log: Logger = LoggerFactory.getLogger(IdenthendelseConsumer::class.java)
        val opprettTask = false // Skal fjernes når test i preprod er fullført.
    }
}
