package no.nav.familie.ba.mottak.config


import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.kafka.listener.ErrorHandler


class CustomKafkaLoggingErrorHandler : ErrorHandler {
    override fun handle(thrownException: Exception, record: ConsumerRecord<*, *>?) {
        LOGGER.error("Problemer med prosessering")
        SECURE_LOGGER.error("Problemer med prosessering av $record", thrownException)
    }

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(CustomKafkaLoggingErrorHandler::class.java)
        val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
    }
}
