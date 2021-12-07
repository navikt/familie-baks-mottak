package no.nav.familie.ba.mottak.hendelser


import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics

import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
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
class JournalføringHendelseAivenConsumer(val journalhendelseService: JournalhendelseService) {


    val journalføringshendelseAivenConsumerFeilCounter: Counter = Metrics.counter("joark.hendelse.journalføring.feil")

    @KafkaListener(
        id = "familie-ba-sak",
        topics = ["\${JOURNALFOERINGHENDELSE_V1_TOPIC_AIVEN_URL}"],
        containerFactory = "kafkaAivenHendelseListenerContainerFactory",
        idIsGroup = false
    )
    @Transactional
    fun listen(consumerRecord: ConsumerRecord<Long, JournalfoeringHendelseRecord>, ack: Acknowledgment) {
        try {
            logger.info("Journalføringshendelse fra aiven mottatt. Offset: ${consumerRecord.offset()}")
            //secureLogger.info("Journalføringshendelse fra aiven mottatt. Offset: ${consumerRecord.offset()} Key: ${consumerRecord.key()} Value: ${consumerRecord.value()}")
            //journalhendelseService.prosesserNyHendelse(consumerRecord, ack)
            ack.acknowledge()
        } catch (e: Exception) {
            journalføringshendelseAivenConsumerFeilCounter.increment()
            secureLogger.error("Feil i prosessering av Journalføringshendelse fra aiven consumerRecord=$consumerRecord", e)
            throw RuntimeException("Feil i prosessering av Journalføringshendelse fra aiven")
        } finally {
            MDC.clear()
        }
    }




    companion object {
        private val logger: Logger = LoggerFactory.getLogger(EnsligForsørgerInfotrygdHendelseConsumer::class.java)
        private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")
    }
}
