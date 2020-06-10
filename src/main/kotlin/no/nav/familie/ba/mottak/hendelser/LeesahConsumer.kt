package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.domene.hendelser.PdlHendelse
import no.nav.person.pdl.leesah.Personhendelse
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.time.LocalDate
import javax.transaction.Transactional

@Service
@Profile("!e2e")
class LeesahConsumer(val hendelsesloggRepository: HendelsesloggRepository,
                     val leesahService: LeesahService) {

    val leesahFeiletCounter: Counter = Metrics.counter("barnetrygd.hendelse.leesha.feilet")


    @KafkaListener(topics = ["aapen-person-pdl-leesah-v1"],
                   id = "personhendelse",
                   idIsGroup = false,
                   containerFactory = "kafkaLeesahListenerContainerFactory")
    @Transactional
    fun listen(cr: ConsumerRecord<Int, Personhendelse>, ack: Acknowledgment) {
        var pdlHendelse = PdlHendelse(cr.value().hentHendelseId(),
                                      cr.offset(),
                                      cr.value().hentOpplysningstype(),
                                      cr.value().hentEndringstype(),
                                      cr.value().hentPersonidenter(),
                                      cr.value().hentDødsdato(),
                                      cr.value().hentFødselsdato()
        )

        try {
            if (hendelsesloggRepository.existsByHendelseIdAndConsumer(pdlHendelse.hendelseId, CONSUMER_PDL)) {
                ack.acknowledge()
                return
            }
            SECURE_LOGGER.info("Har mottatt leesah-hendelse $cr")
            leesahService.prosesserNyHendelse(pdlHendelse)
        } catch (e: RuntimeException) {
            leesahFeiletCounter.increment()
            SECURE_LOGGER.error("Feil i prosessering av leesah-hendelser", e)
            throw RuntimeException("Feil i prosessering av leesah-hendelser")
        }

        ack.acknowledge()
    }

    private fun GenericRecord.hentOpplysningstype() =
            get("opplysningstype").toString()


    private fun GenericRecord.hentPersonidenter() =
            (get("personidenter") as GenericData.Array<*>)
                    .map { it.toString() }

    private fun GenericRecord.hentEndringstype() =
            get("endringstype").toString()

    private fun GenericRecord.hentHendelseId() =
            get("hendelseId").toString()

    private fun GenericRecord.hentDødsdato(): LocalDate? {
        return try {
            val dato = (get("doedsfall") as GenericRecord?)?.get("doedsdato")

            // Integrasjonstester bruker EmbeddedKafka, der en datoverdi tolkes direkte som en LocalDate.
            // I prod tolkes datoer som en Integer.
            when (dato) {
                null -> null
                is LocalDate -> dato
                else -> LocalDate.ofEpochDay((dato as Int).toLong())
            }
        } catch (exception: Exception) {
            log.error("Deserialisering av dødsdato feiler")
            throw exception
        }
    }

    private fun GenericRecord.hentFødselsdato(): LocalDate? {
        return try {
            val dato = (get("foedsel") as GenericRecord?)?.get("foedselsdato")

            when (dato) {
                null -> null
                is LocalDate -> dato
                else -> LocalDate.ofEpochDay((dato as Int).toLong())
            }
        } catch (exception: Exception) {
            log.error("Deserialisering av fødselsdato feiler")
            throw exception
        }
    }

    companion object {
        private val CONSUMER_PDL = HendelseConsumer.PDL
        val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
        val log: Logger = LoggerFactory.getLogger(LeesahConsumer::class.java)
    }
}
