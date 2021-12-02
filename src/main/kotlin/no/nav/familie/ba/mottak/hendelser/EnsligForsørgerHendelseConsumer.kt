package no.nav.familie.ba.mottak.hendelser

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
class EnsligForsørgerHendelseConsumer(val vedtakOmOvergangsstønadService: EnsligForsørgerHendelseService) {

    @KafkaListener(
        id = "efhendelse",
        topics = ["teamfamilie.$TOPIC"],
        containerFactory = "kafkaAivenEFHendelseListenerContainerFactory"
    )
    @Transactional
    fun listenInfotrygd(consumerRecord: ConsumerRecord<String, String>, ack: Acknowledgment) {
        logger.info("$TOPIC Aiven melding mottatt. Offset: ${consumerRecord.offset()}")
        secureLogger.info("$TOPIC Aiven melding mottatt. Offset: ${consumerRecord.offset()} Key: ${consumerRecord.key()} Value: ${consumerRecord.value()}")
        //vedtakOmOvergangsstønadService.prosesserNyHendelse(consumerRecord, ack)
        //ack.acknowledge()
    }

    companion object {
        private const val TOPIC = "aapen-ef-overgangstonad-v1"

        private val logger: Logger = LoggerFactory.getLogger(EnsligForsørgerHendelseConsumer::class.java)
        private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")
    }
}