package no.nav.familie.baks.mottak.config

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.kafka.KafkaException
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler
import org.springframework.kafka.listener.ListenerUtils
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * hentet fra pensr, og skrevet om litt
 */
@Component
class KafkaRestartingErrorHandler : CommonContainerStoppingErrorHandler() {

    val LOGGER: Logger = LoggerFactory.getLogger(KafkaRestartingErrorHandler::class.java)
    val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")

    private val executor: Executor
    private val counter = AtomicInteger(0)
    private val lastError = AtomicLong(0)
    override fun handleRemaining(
        e: Exception,
        records: List<ConsumerRecord<*, *>>?,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer,
    ) {
        Thread.sleep(1000)

        if (records.isNullOrEmpty()) {
            LOGGER.warn("Feil ved konsumering av melding. Ingen records. ${consumer.subscription()}", e)
            scheduleRestart(
                e,
                container,
                "Ukjent topic",
            )
        } else {
            records.first().run {
                LOGGER.warn(
                    "Feil ved konsumering av melding fra ${this.topic()}. id ${this.key()}, " +
                        "offset: ${this.offset()}, partition: ${this.partition()}",
                )
                SECURE_LOGGER.warn("${this.topic()} - Problemer med prosessering av $records", e)
                scheduleRestart(
                    e,
                    container,
                    this.topic(),
                )
            }
        }
    }

    private fun scheduleRestart(
        e: Exception,
        container: MessageListenerContainer,
        topic: String,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastError.getAndSet(now) > COUNTER_RESET_TIME) { // Sjekker om perioden som det ventes er større enn counter_reset_time
            if (counter.get() > 0) {
                LOGGER.error(
                    "Feil ved prosessering av kafkamelding for $topic. Container har restartet ${counter.get()} ganger og " +
                        "man må se på hvorfor record ikke kan leses. " +
                        "Hvis denne meldingen gjentar seg hver ${Duration.ofMillis(LONG_SLEEP)} så klarer ikke tjenesten å hente seg inn",
                )
            }
            counter.set(0)
        }
        val numErrors = counter.incrementAndGet()
        val stopTime =
            if (numErrors > SLOW_ERROR_COUNT) LONG_SLEEP else SHORT_SLEEP * numErrors
        executor.execute {
            try {
                Thread.sleep(stopTime)
                LOGGER.info("Starter kafka container for $topic")
                container.start()
            } catch (exception: Exception) {
                LOGGER.error("Feil oppstod ved venting og oppstart av kafka container", exception)
            }
        }
        stopContainer(container) // i stedet for stopContainer i handleRemaining i parent som kaster error

        throw KafkaException("Stopper kafka container ${counter.get()} for $topic i ${Duration.ofMillis(stopTime)} antall feil $numErrors", KafkaException.Level.WARN, e)
    }

    /**
     * Stopper container. Hentet fra parent, men denne kaster WARN i stedet for ERROR for å begrense alarmer
     */
    private fun stopContainer(container: MessageListenerContainer) {
        this.executor.execute {
            container.stop {}
        }
        // isRunning is false before the container.stop() waits for listener thread
        try {
            ListenerUtils.stoppableSleep(container, 10000) // NOSONAR
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private val LONG_SLEEP = Duration.ofHours(3).toMillis()
        private val SHORT_SLEEP = Duration.ofSeconds(20).toMillis()
        private const val SLOW_ERROR_COUNT = 10
        private val COUNTER_RESET_TIME =
            SHORT_SLEEP * SLOW_ERROR_COUNT * 2 // 10 min
    }

    init {
        this.executor = SimpleAsyncTaskExecutor()
    }
}
