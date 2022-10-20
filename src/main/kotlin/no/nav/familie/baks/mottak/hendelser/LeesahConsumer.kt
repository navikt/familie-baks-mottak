package no.nav.familie.baks.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.log.mdc.MDCConstants
import no.nav.person.pdl.leesah.Personhendelse
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.time.LocalDate
import javax.transaction.Transactional

@Service
@ConditionalOnProperty(
    value = ["funksjonsbrytere.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class LeesahConsumer(val leesahService: LeesahService) {

    val leesahFeiletCounter: Counter = Metrics.counter("barnetrygd.hendelse.leesha.feilet")

    @KafkaListener(
        groupId = "baks-mottak-leesah-1",
        topics = ["pdl.leesah-v1"],
        id = "leesah-1",
        idIsGroup = false,
        containerFactory = "kafkaAivenHendelseListenerContainerFactory"
    )
    @Transactional
    fun listen(cr: ConsumerRecord<String, Personhendelse>, ack: Acknowledgment) {
        SECURE_LOGGER.info("LeesahConsumer har mottatt hendelse $cr")
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
        return deserialiserDatofeltFraSubrecord("doedsfall", "doedsdato")
    }

    private fun GenericRecord.hentFødselsdato(): LocalDate? {
        return deserialiserDatofeltFraSubrecord("foedsel", "foedselsdato")
    }

    private fun GenericRecord.hentFødeland(): String? {
        return (get("foedsel") as GenericRecord?)?.get("foedeland")?.toString()
    }

    private fun GenericRecord.hentTidligereHendelseId(): String? {
        return get("tidligereHendelseId")?.toString()
    }

    private fun GenericRecord.hentUtflyttingsdato(): LocalDate? {
        return deserialiserDatofeltFraSubrecord("utflyttingFraNorge", "utflyttingsdato")
    }

    private fun GenericRecord.hentSivilstandType(): String? {
        return (get("sivilstand") as GenericRecord?)?.get("type")?.toString()
    }

    private fun GenericRecord.hentSivilstandDato(): LocalDate? {
        return deserialiserDatofeltFraSubrecord("sivilstand", "gyldigFraOgMed")
            ?: deserialiserDatofeltFraSubrecord("sivilstand", "bekreftelsesdato")
        // Fra pdldocs: bekreftelsesdato kun tilgjengelig når gyldighetsdato er ukjent.
    }

    private fun GenericRecord.deserialiserDatofeltFraSubrecord(
        subrecord: String,
        datofelt: String
    ): LocalDate? {
        return try {
            val dato = (get(subrecord) as GenericRecord?)?.get(datofelt)

            // Integrasjonstester bruker EmbeddedKafka, der en datoverdi tolkes direkte som en LocalDate.
            // I prod tolkes datoer som en Integer.
            when (dato) {
                null -> null
                is LocalDate -> dato
                else -> LocalDate.ofEpochDay((dato as Int).toLong())
            }
        } catch (exception: Exception) {
            log.error("Deserialisering av $datofelt feiler")
            throw exception
        }
    }

    companion object {

        val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
        val log: Logger = LoggerFactory.getLogger(LeesahConsumer::class.java)
    }
}
