package no.nav.familie.baks.mottak.config

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerConfigUtils
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import java.time.Duration

@Configuration
@ConditionalOnProperty(
    value = ["funksjonsbrytere.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class KafkaAivenConfig(val environment: Environment) {
    @Bean
    fun kafkaAivenHendelseListenerContainerFactory(kafkaRestartingErrorHandler: KafkaRestartingErrorHandler): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.containerProperties.authExceptionRetryInterval = Duration.ofSeconds(2)
        factory.consumerFactory = DefaultKafkaConsumerFactory(consumerConfigs())
        factory.setCommonErrorHandler(kafkaRestartingErrorHandler)
        return factory
    }

    @Bean
    fun kafkaAivenHendelseListenerAvroLatestContainerFactory(kafkaRestartingErrorHandler: KafkaRestartingErrorHandler): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.containerProperties.authExceptionRetryInterval = Duration.ofSeconds(2)
        factory.consumerFactory = DefaultKafkaConsumerFactory(consumerConfigsLatestAvro())
        factory.setCommonErrorHandler(kafkaRestartingErrorHandler)
        return factory
    }

    @Bean
    fun kafkaAivenHendelseListenerAvroEarliestContainerFactory(kafkaRestartingErrorHandler: KafkaRestartingErrorHandler): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.containerProperties.authExceptionRetryInterval = Duration.ofSeconds(2)
        factory.consumerFactory = DefaultKafkaConsumerFactory(consumerConfigsEarliestAvro())
        factory.setCommonErrorHandler(kafkaRestartingErrorHandler)
        return factory
    }

    @Bean(name = [KafkaListenerConfigUtils.KAFKA_LISTENER_ENDPOINT_REGISTRY_BEAN_NAME])
    fun kafkaListenerEndpointRegistry(): KafkaListenerEndpointRegistry? {
        return KafkaListenerEndpointRegistry()
    }

    private fun consumerConfigs(): Map<String, Any> {
        val kafkaBrokers = System.getenv("KAFKA_BROKERS") ?: "http://localhost:9092"
        val consumerConfigs =
            mutableMapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.CLIENT_ID_CONFIG to "consumer-familie-baks-mottak-1",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "50",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            )
        if (environment.activeProfiles.none { it.contains("dev") || it.contains("postgres") }) {
            return consumerConfigs + securityConfig()
        }
        return consumerConfigs.toMap()
    }

    private fun consumerConfigsLatestAvro(): Map<String, Any> {
        val kafkaBrokers = System.getenv("KAFKA_BROKERS") ?: "http://localhost:9092"
        val schemaRegisty = System.getenv("KAFKA_SCHEMA_REGISTRY") ?: "http://localhost:9093"
        val schemaRegistryUser = System.getenv("KAFKA_SCHEMA_REGISTRY_USER") ?: "mangler i pod"
        val schemaRegistryPassword = System.getenv("KAFKA_SCHEMA_REGISTRY_PASSWORD") ?: "mangler i pod"
        val consumerConfigs =
            mutableMapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
                "schema.registry.url" to schemaRegisty,
                "basic.auth.credentials.source" to "USER_INFO",
                "basic.auth.user.info" to "$schemaRegistryUser:$schemaRegistryPassword",
                "specific.avro.reader" to true,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                ConsumerConfig.CLIENT_ID_CONFIG to "consumer-familie-baks-mottak-2",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
            )
        if (environment.activeProfiles.none { it.contains("dev") || it.contains("postgres") }) {
            return consumerConfigs + securityConfig()
        }
        return consumerConfigs.toMap()
    }

    private fun consumerConfigsEarliestAvro(): Map<String, Any> {
        val kafkaBrokers = System.getenv("KAFKA_BROKERS") ?: "http://localhost:9092"
        val schemaRegisty = System.getenv("KAFKA_SCHEMA_REGISTRY") ?: "http://localhost:9093"
        val schemaRegistryUser = System.getenv("KAFKA_SCHEMA_REGISTRY_USER") ?: "mangler i pod"
        val schemaRegistryPassword = System.getenv("KAFKA_SCHEMA_REGISTRY_PASSWORD") ?: "mangler i pod"
        val consumerConfigs =
            mutableMapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
                "schema.registry.url" to schemaRegisty,
                "basic.auth.credentials.source" to "USER_INFO",
                "basic.auth.user.info" to "$schemaRegistryUser:$schemaRegistryPassword",
                "specific.avro.reader" to true,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                ConsumerConfig.CLIENT_ID_CONFIG to "consumer-familie-baks-mottak-2",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "50",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            )
        if (environment.activeProfiles.none { it.contains("dev") || it.contains("postgres") }) {
            return consumerConfigs + securityConfig()
        }
        return consumerConfigs.toMap()
    }

    private fun securityConfig(): Map<String, Any> {
        val kafkaTruststorePath = System.getenv("KAFKA_TRUSTSTORE_PATH")
        val kafkaCredstorePassword = System.getenv("KAFKA_CREDSTORE_PASSWORD")
        val kafkaKeystorePath = System.getenv("KAFKA_KEYSTORE_PATH")
        return mapOf(
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SSL",
            // Disable server host name verification
            SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
            SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "JKS",
            SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
            SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to kafkaTruststorePath,
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
            SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to kafkaKeystorePath,
            SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
            SslConfigs.SSL_KEY_PASSWORD_CONFIG to kafkaCredstorePassword,
        )
    }
}
