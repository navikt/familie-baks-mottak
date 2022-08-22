package no.nav.familie.ba.mottak.hendelser

import com.fasterxml.jackson.annotation.JsonProperty
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
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
import javax.transaction.Transactional

/**
 * Lytter på en kafka topic aapen-ef-overgangstonad-v1 fra infotrygd og sender til ba-sak. Retention på topic er satt til -1 i prod
 *
 * https://confluence.adeo.no/display/MODNAV/Data+til+BA-sak%2C+hendelser#
 * Melding på format:
 * <code>
 {
 "table": "INFOTRYGD_Q1.T_HENDELSE",
 "op_type": "I",
 "op_ts": "2021-11-11 17:49:51.000000",
 "current_ts": "2021-11-11T17:49:55.821000",
 "pos": "00000000300000004241",
 "after": {
 "HENDELSE_ID": 10343774,
 "TYPE_HENDELSE": "INNVILGET      ",
 "AKTOR_ID": "2424242424241       ",
 "TYPE_YTELSE": "EF",
 "IDENTDATO": "20210801",
 "FOM": "2021-08-01 00:00:00",
 "SATS": 8820.00,
 "KOBLING_ID": 0,
 "BRUKERID": "K278CP10",
 "TIDSPUNKT_REG": "2021-11-11 17:49:36.690083000",
 "OPPRETTET": "2021-11-11 17:49:44.361512000",
 "OPPDATERT": "2021-11-11 17:49:44.361512000",
 "DB_SPLITT": "  "
 },
}
 * </code>
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
        groupId = "ba-mottak-ef-infotrygd-1",
        id = "ef-infotrygd-overgangstonad",
        topics = ["teamfamilie.$TOPIC_INFOTRYGD_VEDTAK"],
        containerFactory = "kafkaAivenHendelseListenerContainerFactory",
        idIsGroup = false
    )
    @Transactional
    fun listen(consumerRecord: ConsumerRecord<String, String>, ack: Acknowledgment) {
        try {
            logger.info("$TOPIC_INFOTRYGD_VEDTAK melding mottatt. Offset: ${consumerRecord.offset()}")
            secureLogger.info("$TOPIC_INFOTRYGD_VEDTAK melding mottatt. Offset: ${consumerRecord.offset()} Key: ${consumerRecord.key()} Value: ${consumerRecord.value()}")
            objectMapper.readValue(consumerRecord.value(), EnsligForsørgerInfotrygdHendelse::class.java)
                .also {
                    MDC.put(MDCConstants.MDC_CALL_ID, "CallId_ef_infotrygd_${it.after.hendelseId}")
                    vedtakOmOvergangsstønadService.prosesserEfInfotrygdHendelse(consumerRecord.offset(), it.after)
                }
            ack.acknowledge()
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

data class EnsligForsørgerInfotrygdHendelse(val after: InfotrygdHendelse)

data class InfotrygdHendelse(
    @JsonProperty("HENDELSE_ID") val hendelseId: String,
    @JsonProperty("TYPE_HENDELSE") val typeHendelse: String,
    @JsonProperty("AKTOR_ID") val aktørId: Long,
    @JsonProperty("TYPE_YTELSE") val typeYtelse: String,
    @JsonProperty("IDENTDATO") val identdato: String,
    @JsonProperty("FOM") val fom: String,
    @JsonProperty("SATS") val sats: Double,
    @JsonProperty("KOBLING_ID") val koblingId: Long
)
