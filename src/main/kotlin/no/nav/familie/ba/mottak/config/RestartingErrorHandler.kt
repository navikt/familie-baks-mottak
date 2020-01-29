package no.nav.familie.ba.mottak.config

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.ContainerAwareErrorHandler
import org.springframework.kafka.listener.ContainerStoppingErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.scheduling.TaskScheduler
import java.time.Instant
import java.time.temporal.ChronoUnit


class RestartingErrorHandler(private val taskScheduler: TaskScheduler) : ContainerAwareErrorHandler {
    override fun handle(thrownException: Exception,
                        data: List<ConsumerRecord<*, *>?>?,
                        consumer: Consumer<*, *>?,
                        container: MessageListenerContainer) {
        taskScheduler.schedule(Restarter(container), Instant.now().plus(1, ChronoUnit.MINUTES))
        STOPPER.handle(thrownException, data, consumer, container)
    }

    private class Restarter(val container: MessageListenerContainer) : Runnable {
        override fun run() {
            container.start()
        }
    }

    companion object {
        private val STOPPER = ContainerStoppingErrorHandler()
    }
}
