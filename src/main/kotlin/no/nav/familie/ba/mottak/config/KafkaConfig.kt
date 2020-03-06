package no.nav.familie.ba.mottak.config

import org.apache.avro.generic.GenericRecord
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.scheduling.TaskScheduler

@EnableKafka
@Configuration
class KafkaConfig {

    @Bean
    fun consumerFactory(properties: KafkaProperties): ConsumerFactory<Int, GenericRecord> {
        return DefaultKafkaConsumerFactory(properties.buildConsumerProperties())
    }

    @Bean
    fun restartingErrorHandler(taskScheduler: TaskScheduler): RestartingErrorHandler? {
        return RestartingErrorHandler(taskScheduler)
    }

    @Bean
    fun kafkaListenerContainerFactory(consumerFactory: ConsumerFactory<Int, GenericRecord>)
            : ConcurrentKafkaListenerContainerFactory<Int, GenericRecord> {
        val factory = ConcurrentKafkaListenerContainerFactory<Int, GenericRecord>()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.consumerFactory = consumerFactory
        factory.setErrorHandler(CustomKafkaLoggingErrorHandler())
        return factory
    }
}
