package no.nav.familie.ba.mottak.config

import no.nav.person.pdl.leesah.Personhendelse
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
    fun restartingErrorHandler(taskScheduler: TaskScheduler): RestartingErrorHandler? {
        return RestartingErrorHandler(taskScheduler)
    }

    @Bean
    fun kafkaListenerContainerFactory(properties: KafkaProperties)
            : ConcurrentKafkaListenerContainerFactory<Int, Personhendelse> {
        val factory = ConcurrentKafkaListenerContainerFactory<Int, Personhendelse>()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.consumerFactory = DefaultKafkaConsumerFactory(properties.buildConsumerProperties())
        factory.setErrorHandler(CustomKafkaLoggingErrorHandler())
        return factory
    }
}
