package no.nav.familie.ba.mottak.hendelser

import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.Hendelseslogg
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.felles.ef.EnsligForsørgerVedtakhendelse
import no.nav.familie.kontrakter.felles.ef.StønadType
import no.nav.familie.kontrakter.felles.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class EnsligForsørgerHendelseService(
    val sakClient: SakClient,
    val hendelsesloggRepository: HendelsesloggRepository,
) {

    fun prosesserNyHendelse(
        consumerRecord: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        try {
            val hendelseRecord = consumerRecord.value()
            val ensligForsørgerVedtakhendelse = objectMapper.readValue(hendelseRecord, EnsligForsørgerVedtakhendelse::class.java)

            if (ensligForsørgerVedtakhendelse.stønadType == StønadType.OVERGANGSSTØNAD) {
                if (hendelsesloggRepository.existsByHendelseIdAndConsumer(consumerRecord.key(), CONSUMER_EF)) {
                    ack.acknowledge()
                    return
                }
                secureLogger.info("Mottatt vedtak om overgangsstønad hendelse: $hendelseRecord")
                sakClient.sendVedtakOmOvergangsstønadHendelseTilSak(ensligForsørgerVedtakhendelse.personIdent)

                hendelsesloggRepository.save(
                    Hendelseslogg(
                        consumerRecord.offset(),
                        consumerRecord.key(),
                        CONSUMER_EF,
                        mapOf(
                            "behandlingId" to ensligForsørgerVedtakhendelse.behandlingId.toString(),
                        ).toProperties(),
                        ident = ensligForsørgerVedtakhendelse.personIdent
                    )
                )
            }
            ack.acknowledge()
        } catch (e: Exception) {
            logger.error("Feil ved prosessering av EF-hendelse", e)
            throw e
        }
    }

    companion object {
        private val CONSUMER_EF = HendelseConsumer.EF

        private val logger: Logger = LoggerFactory.getLogger(EnsligForsørgerHendelseService::class.java)
        private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")
    }
}
