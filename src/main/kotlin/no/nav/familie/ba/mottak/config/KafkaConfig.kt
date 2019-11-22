package no.nav.familie.ba.mottak.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.confluent.kafka.streams.serdes.avro.GenericAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer

@EnableKafka
@Configuration
class KafkaConfig {

    @Bean
    fun consumerFactory(properties : KafkaProperties) : ConsumerFactory<String, String> {
        return DefaultKafkaConsumerFactory(properties.buildConsumerProperties())
    }

    @Bean
    fun kafkaListenerContainerFactory(consumerFactory: ConsumerFactory<String, String>) : KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>> {
        var factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory
        return factory
    }

    @Bean
    fun kafkaObjectMapper(): ObjectMapper {
        return ObjectMapper()
                .registerModule(JavaTimeModule())
                .registerModule(KotlinModule())
    }

    @Bean
    fun consumerFactoryPersonHendelse(properties: KafkaProperties): ConsumerFactory<String, GenericRecord> {
        return DefaultKafkaConsumerFactory(properties.buildConsumerProperties(),
                StringDeserializer(),
                GenericAvroDeserializer())
    }

    @Bean
    fun kafkaListenerContainerFactoryPersonhendelse(
            consumerFactoryPersonhendelse: ConsumerFactory<String, GenericRecord>): ConcurrentKafkaListenerContainerFactory<String, GenericRecord> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, GenericRecord>()
        factory.consumerFactory = consumerFactoryPersonhendelse
        return factory
    }
}