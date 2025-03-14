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
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@ConditionalOnProperty(
    value = ["funksjonsbrytere.kafka.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class LeesahConsumer(
    val leesahService: LeesahService,
) {
    val leesahFeiletCounter: Counter = Metrics.counter("barnetrygd.hendelse.leesha.feilet")

    @KafkaListener(
        groupId = "baks-mottak-leesah-1",
        topics = ["pdl.leesah-v1"],
        id = "leesah-1",
        idIsGroup = false,
        containerFactory = "kafkaAivenHendelseListenerAvroEarliestContainerFactory",
        autoStartup = "true",
        clientIdPrefix = "leesah",
    )
    @Transactional
    fun listen(
        cr: ConsumerRecord<String, Personhendelse>,
        ack: Acknowledgment,
    ) {
        val pdlHendelse =
            PdlHendelse(
                cr.value().hentHendelseId(),
                cr.key().substring(6),
                cr.offset(),
                cr.value().hentOpplysningstype(),
                cr.value().hentEndringstype(),
                cr.value().hentPersonidenter(),
                cr.value().hentDødsdato(),
                cr.value().hentFødselsdato(),
                cr.value().hentFødeland(),
                cr.value().hentUtflyttingsdato(),
                cr.value().hentTidligereHendelseId(),
                cr.value().hentSivilstandType(),
                cr.value().hentSivilstandDato(),
            )

        try {
            MDC.put(MDCConstants.MDC_CALL_ID, pdlHendelse.hendelseId)
            leesahService.prosesserNyHendelse(pdlHendelse)
        } catch (e: RuntimeException) {
            leesahFeiletCounter.increment()
            SECURE_LOGGER.error("Feil i prosessering av leesah-hendelser", e)
            throw RuntimeException("Feil i prosessering av leesah-hendelser")
        } finally {
            MDC.clear()
        }

        ack.acknowledge()
    }

    private fun GenericRecord.hentOpplysningstype() = get("opplysningstype").toString()

    private fun GenericRecord.hentPersonidenter() =
        (get("personidenter") as GenericData.Array<*>)
            .map { it.toString() }

    private fun GenericRecord.hentEndringstype() = get("endringstype").toString()

    private fun GenericRecord.hentHendelseId() = get("hendelseId").toString()

    private fun GenericRecord.hentDødsdato(): LocalDate? = deserialiserDatofeltFraSubrecord("doedsfall", "doedsdato")

    private fun GenericRecord.hentFødselsdato(): LocalDate? = deserialiserDatofeltFraSubrecord("foedselsdato", "foedselsdato")

    private fun GenericRecord.hentFødeland(): String? = (get("foedsel") as GenericRecord?)?.get("foedeland")?.toString()

    private fun GenericRecord.hentTidligereHendelseId(): String? = get("tidligereHendelseId")?.toString()

    private fun GenericRecord.hentUtflyttingsdato(): LocalDate? = deserialiserDatofeltFraSubrecord("utflyttingFraNorge", "utflyttingsdato")

    private fun GenericRecord.hentSivilstandType(): String? = (get("sivilstand") as GenericRecord?)?.get("type")?.toString()

    private fun GenericRecord.hentSivilstandDato(): LocalDate? {
        return deserialiserDatofeltFraSubrecord("sivilstand", "gyldigFraOgMed")
            ?: deserialiserDatofeltFraSubrecord("sivilstand", "bekreftelsesdato")
        // Fra pdldocs: bekreftelsesdato kun tilgjengelig når gyldighetsdato er ukjent.
    }

    private fun GenericRecord.deserialiserDatofeltFraSubrecord(
        subrecord: String,
        datofelt: String,
    ): LocalDate? =
        try {
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

    companion object {
        val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
        val log: Logger = LoggerFactory.getLogger(LeesahConsumer::class.java)
    }
}
