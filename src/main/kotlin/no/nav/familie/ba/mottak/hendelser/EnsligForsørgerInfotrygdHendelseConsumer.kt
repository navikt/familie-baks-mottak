package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.kontrakter.felles.ef.EnsligForsørgerVedtakhendelse
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.log.mdc.MDCConstants
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.*
import javax.transaction.Transactional


/**
 * Lytter på en kafka topic fra familie-ef-iverksatt og sender til ba-sak. Retention på topic er satt til 720 timer.
 *
 * Melding på format:
 * aapen-ensligforsorger-iverksatt-vedtak melding mottatt. Offset: 142 Key: 1108 Value: {"behandlingId":12345,"personIdent":"12345678901","stønadType":"OVERGANGSSTØNAD"}
 */
@Service
@ConditionalOnProperty(
    value = ["funksjonsbrytere.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class EnsligForsørgerInfotrygdHendelseConsumer(val vedtakOmOvergangsstønadService: EnsligForsørgerHendelseService) {


    val ensligForsørgerInfotrygdHendelseConsumerFeilCounter: Counter = Metrics.counter("ef.hendelse.infotrygdvedtak.feil")

    @KafkaListener(
        id = "ef-infotrygd-overgangstonad",
        topics = ["teamfamilie.$TOPIC_INFOTRYGD_VEDTAK"],
        containerFactory = "kafkaAivenEFHendelseListenerContainerFactory",
        idIsGroup = false
    )
    @Transactional
    fun listen(consumerRecord: ConsumerRecord<String, String>, ack: Acknowledgment) {
        try {
            logger.info("$TOPIC_INFOTRYGD_VEDTAK melding mottatt. Offset: ${consumerRecord.offset()}")
            secureLogger.info("$TOPIC_INFOTRYGD_VEDTAK melding mottatt. Offset: ${consumerRecord.offset()} Key: ${consumerRecord.key()} Value: ${consumerRecord.value()}")

        } catch (e: Exception) {
            ensligForsørgerInfotrygdHendelseConsumerFeilCounter.increment()
            secureLogger.error("Feil i prosessering av $TOPIC_INFOTRYGD_VEDTAK consumerRecord=$consumerRecord", e)
            throw RuntimeException("Feil i prosessering av $TOPIC_INFOTRYGD_VEDTAK")
        } finally {
            MDC.clear()
        }
    }

    companion object {

        private const val TOPIC_INFOTRYGD_VEDTAK = "aapen-ef-overgangstonad-v1"

        private val logger: Logger = LoggerFactory.getLogger(EnsligForsørgerInfotrygdHendelseConsumer::class.java)
        private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")
    }
}