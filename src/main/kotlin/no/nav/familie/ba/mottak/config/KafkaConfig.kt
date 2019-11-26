package no.nav.familie.ba.mottak.config

import org.apache.avro.generic.GenericRecord
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory

@EnableKafka
@Configuration
class KafkaConfig {

    @Bean
    fun consumerFactory(properties : KafkaProperties) : ConsumerFactory<String, GenericRecord> {
        return DefaultKafkaConsumerFactory(properties.buildConsumerProperties())
    }

    @Bean
    fun kafkaListenerContainerFactory(consumerFactory: ConsumerFactory<String, GenericRecord>) : ConcurrentKafkaListenerContainerFactory<String, GenericRecord> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, GenericRecord>()
        factory.consumerFactory = consumerFactory
        return factory
    }
}