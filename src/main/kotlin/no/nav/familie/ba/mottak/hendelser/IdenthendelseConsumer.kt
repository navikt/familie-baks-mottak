package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.felles.PersonIdent
import no.nav.familie.log.mdc.MDCConstants
import no.nav.person.pdl.aktor.v2.Aktor
import no.nav.person.pdl.aktor.v2.Type
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.UUID
import javax.transaction.Transactional

@Service
@ConditionalOnProperty(
    value = ["funksjonsbrytere.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class IdenthendelseConsumer(private val sakClient: SakClient) {

    val identhendelseFeiletCounter: Counter = Metrics.counter("barnetrygd.hendelse.ident.feilet")

    @KafkaListener(
        topics = ["aapen-person-pdl-aktor-v1"],
        id = "identhendelse",
        idIsGroup = false,
        containerFactory = "kafkaIdenthendelseListenerContainerFactory"
    )
    @Transactional
    fun listen(consumerRecord: ConsumerRecord<String, Aktor>, ack: Acknowledgment) {
        try {
            MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
            SECURE_LOGGER.info("Har mottatt ident-hendelse $consumerRecord")
            val aktør = consumerRecord.value()
            val folkeregisterident = aktør.identifikatorer.single { it.type == Type.FOLKEREGISTERIDENT && it.gjeldende }
            if (opprettTask) {
                sakClient.sendIdenthendelseTilSak(PersonIdent(ident = folkeregisterident.idnummer.toString()))
            }
        } catch (e: RuntimeException) {
            identhendelseFeiletCounter.increment()
            SECURE_LOGGER.error("Feil i prosessering av ident-hendelser", e)
            throw RuntimeException("Feil i prosessering av ident-hendelser")
        } finally {
            MDC.clear()
        }

        ack.acknowledge()
    }

    companion object {

        val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
        val log: Logger = LoggerFactory.getLogger(IdenthendelseConsumer::class.java)
        val opprettTask = false // Skal fjernes når test i preprod er fullført.
    }
}
