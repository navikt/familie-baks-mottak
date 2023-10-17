package no.nav.familie.baks.mottak.hendelser

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
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

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
    matchIfMissing = true,
)
class EnsligForsørgerVedtakHendelseConsumer(val vedtakOmOvergangsstønadService: EnsligForsørgerHendelseService) {
    val ensligForsørgerVedtakhendelseFeilCounter: Counter = Metrics.counter("ef.hendelse.vedtak.feil")

    @KafkaListener(
        groupId = "baks-mottak-ef-vedtak-1",
        id = "efhendelse",
        topics = ["teamfamilie.$TOPIC_EF_VEDTAK"],
        containerFactory = "kafkaAivenHendelseListenerContainerFactory",
        idIsGroup = false,
    )
    @Transactional
    fun listen(
        consumerRecord: ConsumerRecord<String, String>,
        ack: Acknowledgment,
    ) {
        try {
            MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
            logger.info("$TOPIC_EF_VEDTAK melding mottatt. Offset: ${consumerRecord.offset()}")
            secureLogger.info("$TOPIC_EF_VEDTAK melding mottatt. Offset: ${consumerRecord.offset()} Key: ${consumerRecord.key()} Value: ${consumerRecord.value()}")
            objectMapper.readValue(consumerRecord.value(), EnsligForsørgerVedtakhendelse::class.java)
                .also {
                    vedtakOmOvergangsstønadService.prosesserEfVedtakHendelse(consumerRecord.offset(), it)
                }
            ack.acknowledge()
        } catch (e: Exception) {
            ensligForsørgerVedtakhendelseFeilCounter.increment()
            secureLogger.error("Feil i prosessering av $TOPIC_EF_VEDTAK consumerRecord=$consumerRecord", e)
            throw RuntimeException("Feil i prosessering av $TOPIC_EF_VEDTAK")
        } finally {
            MDC.clear()
        }
    }

    companion object {
        private const val TOPIC_EF_VEDTAK = "aapen-ensligforsorger-iverksatt-vedtak"

        private val logger: Logger = LoggerFactory.getLogger(EnsligForsørgerVedtakHendelseConsumer::class.java)
        private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")
    }
}
