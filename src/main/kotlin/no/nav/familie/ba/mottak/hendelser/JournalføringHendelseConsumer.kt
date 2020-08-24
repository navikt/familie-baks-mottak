package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import javax.transaction.Transactional


@Service
@Profile("!e2e")
class JournalføringHendelseConsumer(val journalhendelseService: JournalhendelseService) {

    @KafkaListener(id = "familie-ba-mottak",
                   topics = ["\${JOURNALFOERINGHENDELSE_V1_TOPIC_URL}"],
                   containerFactory = "kafkaJournalføringHendelseListenerContainerFactory",
                   idIsGroup = false
    )
    @Transactional
    fun listen(consumerRecord: ConsumerRecord<Long, JournalfoeringHendelseRecord>, ack: Acknowledgment) {
        journalhendelseService.prosesserNyHendelse(consumerRecord, ack)
    }



}