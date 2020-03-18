package no.nav.familie.ba.mottak.config

import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.apache.avro.generic.GenericRecord
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.scheduling.TaskScheduler

@EnableKafka
@Configuration
class KafkaConfig {

    @Bean
    fun kafkaLeesahListenerContainerFactory(properties: KafkaProperties, kafkaErrorHandler: KafkaErrorHandler)
            : ConcurrentKafkaListenerContainerFactory<Int, GenericRecord> {
        val factory = ConcurrentKafkaListenerContainerFactory<Int, GenericRecord>()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.consumerFactory = DefaultKafkaConsumerFactory(properties.buildConsumerProperties())
        factory.setErrorHandler(kafkaErrorHandler)
        return factory
    }

    @Bean
    fun kafkaJournalføringHendelseListenerContainerFactory(properties: KafkaProperties, kafkaErrorHandler: KafkaErrorHandler)
            : ConcurrentKafkaListenerContainerFactory<Int, JournalfoeringHendelseRecord> {
        val factory = ConcurrentKafkaListenerContainerFactory<Int, JournalfoeringHendelseRecord>()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.consumerFactory = DefaultKafkaConsumerFactory(properties.buildConsumerProperties())
        factory.setErrorHandler(kafkaErrorHandler)
        return factory
    }
}
