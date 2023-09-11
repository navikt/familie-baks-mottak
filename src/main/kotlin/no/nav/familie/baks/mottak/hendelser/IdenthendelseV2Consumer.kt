package no.nav.familie.baks.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.integrasjoner.SakClient
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
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@ConditionalOnProperty(
    value = ["funksjonsbrytere.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class IdenthendelseV2Consumer(
    private val sakClient: SakClient,
) {

    val identhendelseFeiletCounter: Counter = Metrics.counter("barnetrygd.hendelse.ident.feilet")

    @KafkaListener(
        groupId = "familie-baks-mottak-aktor-v2",
        topics = ["pdl.aktor-v2"],
        id = "baks-aktor-v2",
        idIsGroup = false,
        containerFactory = "kafkaAivenHendelseListenerAvroLatestContainerFactory",
    )
    @Transactional
    fun listen(consumerRecord: ConsumerRecord<String, Aktor?>, ack: Acknowledgment) {
        try {
            Thread.sleep(60000) // Sender man med en gang, så får man Person ikke funnet fra PDL når ba-sak gjør filtrering. Venter derfor 1 minutt
            MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
            SECURE_LOGGER.info("Har mottatt ident-hendelse $consumerRecord")

            val aktør = consumerRecord.value()
            val aktørIdPåHendelse = consumerRecord.key()

            if (aktør == null) {
                log.warn("Tom aktør fra identhendelse")
                SECURE_LOGGER.warn("Tom aktør fra identhendelse med nøkkel $aktørIdPåHendelse")
            }

            val aktivAktørid = aktør?.identifikatorer?.singleOrNull { ident ->
                ident.type == Type.AKTORID && ident.gjeldende
            }?.idnummer.toString()

            if (aktørIdPåHendelse == aktivAktørid) { // I tilfeller som ved merge av hendelser vil man få både identhendelse på gammel og ny aktørid, så for å unngå duplikater så sender man bare på aktiv ident
                aktør?.identifikatorer?.singleOrNull { ident ->
                    ident.type == Type.FOLKEREGISTERIDENT && ident.gjeldende
                }?.also { folkeregisterident ->
                    SECURE_LOGGER.info("Sender ident-hendelse til ba-sak for ident $folkeregisterident")

                    sakClient.sendIdenthendelseTilSak(PersonIdent(ident = folkeregisterident.idnummer.toString()))
                }
            } else {
                SECURE_LOGGER.info("Ignorerer å sende ident-hendelse til ba-sak for aktør $aktørIdPåHendelse ikke lenger gyldig aktør")
            }
        } catch (e: RuntimeException) {
            identhendelseFeiletCounter.increment()
            log.warn("Feil i prosessering av ident-hendelser", e)
            SECURE_LOGGER.warn("Feil i prosessering av ident-hendelser $consumerRecord", e)
            throw RuntimeException("Feil i prosessering av ident-hendelser")
        } finally {
            MDC.clear()
        }

        ack.acknowledge()
    }

    companion object {

        val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
        val log: Logger = LoggerFactory.getLogger(IdenthendelseV2Consumer::class.java)
    }
}
