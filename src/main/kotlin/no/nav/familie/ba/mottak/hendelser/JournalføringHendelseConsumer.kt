package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.Hendelseslogg
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.log.IdUtils
import no.nav.familie.log.mdc.MDCConstants
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import javax.transaction.Transactional


@Service
class JournalføringHendelseConsumer(val hendelsesloggRepository: HendelsesloggRepository,
                                    val journalhendelseService: JournalhendelseService) {

    val feilCounter: Counter = Metrics.counter("barnetrygd.journalhendelse.feilet")

    val logger: Logger = LoggerFactory.getLogger(JournalføringHendelseConsumer::class.java)
    val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

    @KafkaListener(id = "familie-ba-mottak",
                   topics = ["\${JOURNALFOERINGHENDELSE_V1_TOPIC_URL}"],
                   containerFactory = "kafkaJournalføringHendelseListenerContainerFactory",
                   idIsGroup = false
    )
    @Transactional
    fun listen(consumerRecord: ConsumerRecord<Long, JournalfoeringHendelseRecord>, ack: Acknowledgment) {
        try {
            val hendelseRecord = consumerRecord.value()
            val callId = hendelseRecord.kanalReferanseId.toStringOrNull() ?: IdUtils.generateId()
            MDC.put(MDCConstants.MDC_CALL_ID, callId)

            if (hendelsesloggRepository.existsByHendelseIdAndConsumer(hendelseRecord.hendelsesId.toString(), CONSUMER_JOURNAL)) {
                ack.acknowledge()
                return
            }

            if (erGyldigHendelsetype(hendelseRecord)) {
                secureLogger.info("Mottatt gyldig hendelse: $hendelseRecord")
                journalhendelseService.behandleJournalhendelse(hendelseRecord)
            }

            hendelsesloggRepository.save(Hendelseslogg(consumerRecord.offset(),
                                                       hendelseRecord.hendelsesId.toString(),
                                                       CONSUMER_JOURNAL,
                                                       mapOf("journalpostId" to hendelseRecord.journalpostId.toString(),
                                                             "hendelsesType" to hendelseRecord.hendelsesType.toString()).toProperties()
            ))
            ack.acknowledge()
        } catch (e: Exception) {
            logger.error("Feil ved lesing av journalhendelser ", e)
            feilCounter.count()
            throw e
        } finally {
            MDC.clear()
        }
    }

    fun CharSequence.toStringOrNull(): String? {
        return if (!this.isBlank()) this.toString() else null
    }


    private fun erGyldigHendelsetype(hendelseRecord: JournalfoeringHendelseRecord): Boolean {
        return GYLDIGE_HENDELSE_TYPER.contains(hendelseRecord.hendelsesType.toString())
               && (hendelseRecord.temaNytt != null && hendelseRecord.temaNytt.toString() == "BAR")
    }

    companion object {
        private val GYLDIGE_HENDELSE_TYPER = arrayOf("MidlertidigJournalført", "TemaEndret")
        private val CONSUMER_JOURNAL = HendelseConsumer.JOURNAL
    }
}