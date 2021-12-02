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
        topics = ["teamfamilie.$TOPIC_EF_VEDTAK"],
        containerFactory = "kafkaAivenEFHendelseListenerContainerFactory",
        idIsGroup = false
    )
    @Transactional
    fun listenEfIverksett(consumerRecord: ConsumerRecord<String, String>, ack: Acknowledgment) {
        logger.info("$TOPIC_EF_VEDTAK melding mottatt. Offset: ${consumerRecord.offset()}")
        secureLogger.info("$TOPIC_EF_VEDTAK melding mottatt. Offset: ${consumerRecord.offset()} Key: ${consumerRecord.key()} Value: ${consumerRecord.value()}")
        //vedtakOmOvergangsstønadService.prosesserNyHendelse(consumerRecord, ack)
        //ack.acknowledge()
    }

    companion object {
        private const val TOPIC_EF_VEDTAK = "aapen-ensligforsorger-iverksatt-vedtak"
        private const val TOPIC_INFOTRYGD_VEDTAK = "aapen-ef-overgangstonad-v1"

        private val logger: Logger = LoggerFactory.getLogger(EnsligForsørgerHendelseConsumer::class.java)
        private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")
    }
}