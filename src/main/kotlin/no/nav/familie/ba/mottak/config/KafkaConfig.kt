package no.nav.familie.ba.mottak.config

import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.apache.avro.generic.GenericRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.ErrorHandler
import java.time.Duration

@EnableKafka
@Configuration
@Profile("!e2e")
class KafkaConfig {



    @Bean
    fun kafkaLeesahListenerContainerFactory(properties: KafkaProperties, kafkaErrorHandler: KafkaErrorHandler, environment: Environment)
            : ConcurrentKafkaListenerContainerFactory<Int, GenericRecord> {
        val factory = ConcurrentKafkaListenerContainerFactory<Int, GenericRecord>()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.containerProperties.authorizationExceptionRetryInterval = Duration.ofSeconds(2)
        factory.consumerFactory = DefaultKafkaConsumerFactory(properties.buildConsumerProperties())
        if (environment.activeProfiles.contains("preprod")) {
            factory.setErrorHandler(KafkaSecureloggingErrorHandler())
        } else {
            factory.setErrorHandler(kafkaErrorHandler)
        }
        return factory
    }





    @Bean
    fun kafkaJournalføringHendelseListenerContainerFactory(properties: KafkaProperties, kafkaErrorHandler: KafkaErrorHandler)
            : ConcurrentKafkaListenerContainerFactory<Long, JournalfoeringHendelseRecord> {
        properties.properties.put("specific.avro.reader", "true")
        val factory = ConcurrentKafkaListenerContainerFactory<Long, JournalfoeringHendelseRecord>()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.containerProperties.authorizationExceptionRetryInterval = Duration.ofSeconds(2)
        factory.consumerFactory = DefaultKafkaConsumerFactory(properties.buildConsumerProperties())
        factory.setErrorHandler(kafkaErrorHandler)
        return factory
    }
}
