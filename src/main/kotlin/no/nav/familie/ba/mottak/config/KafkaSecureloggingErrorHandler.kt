package no.nav.familie.ba.mottak.config

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.LoggingErrorHandler
import org.springframework.stereotype.Component
import org.springframework.util.ObjectUtils

@Component
class KafkaSecureloggingErrorHandler : LoggingErrorHandler() {
    val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")

    override fun handle(thrownException: Exception?, record: ConsumerRecord<*, *>?) {
        SECURE_LOGGER.error("Feil ved prosessering av kafkamelding: " + ObjectUtils.nullSafeToString(record))
    }

}