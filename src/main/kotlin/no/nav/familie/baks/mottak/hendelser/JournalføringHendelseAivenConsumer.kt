package no.nav.familie.baks.mottak.hendelser

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
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnProperty(
    value = ["funksjonsbrytere.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class JournalføringHendelseAivenConsumer(
    val journalhendelseService: JournalhendelseService,
) {
    val journalføringshendelseAivenConsumerFeilCounter: Counter = Metrics.counter("joark.hendelse.journalføring.feil")

    @KafkaListener(
        groupId = "familie-baks-mottak-jfr",
        id = "baks-mottak-journal-hendelser-aiven",
        topics = ["\${JOURNALFOERINGHENDELSE_V1_TOPIC_AIVEN_URL}"],
        containerFactory = "kafkaAivenHendelseListenerAvroLatestContainerFactory",
        idIsGroup = false,
        clientIdPrefix = "jfr",
    )
    @Transactional
    fun listen(
        consumerRecord: ConsumerRecord<Long, JournalfoeringHendelseRecord>,
        ack: Acknowledgment,
    ) {
        try {
            journalhendelseService.prosesserNyHendelse(consumerRecord, ack)
        } catch (e: Exception) {
            journalføringshendelseAivenConsumerFeilCounter.increment()
            logger.error("Feil i prosessering av Journalføringshendelse fra aiven")
            secureLogger.error("Feil i prosessering av Journalføringshendelse fra aiven consumerRecord=$consumerRecord", e)
            throw RuntimeException("Feil i prosessering av Journalføringshendelse fra aiven")
        } finally {
            MDC.clear()
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JournalføringHendelseAivenConsumer::class.java)
        private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")
    }
}
