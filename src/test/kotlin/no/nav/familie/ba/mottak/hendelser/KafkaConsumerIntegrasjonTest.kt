package no.nav.familie.ba.mottak.hendelser

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.util.DbContainerInitializer
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.TaskRepository
import no.nav.familie.prosessering.rest.RestTaskService
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.person.pdl.leesah.doedsfall.Doedsfall
import no.nav.person.pdl.leesah.foedsel.Foedsel
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.avro.generic.GenericRecordBuilder
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Instant
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}"])
@ExtendWith(SpringExtension::class)
@ContextConfiguration(initializers = [DbContainerInitializer::class])
@ActiveProfiles("integrasjonstest")
@EmbeddedKafka(partitions = 1, topics = ["aapen-person-pdl-leesah-v1", "aapen-dok-journalfoering-v1-integrasjonstest"], count = 1)
@Tag("integration")
@Disabled
class KafkaConsumerIntegrasjonTest(@Autowired val hendelsesloggRepository: HendelsesloggRepository,
                                   @Autowired val kafkaProperties: KafkaProperties,
                                   @Autowired val taskRepository: TaskRepository,
                                   @Autowired val restTaskService: RestTaskService
) {

    private fun buildKafkaProducer(): Producer<Int, GenericRecord> {
        val senderProps = kafkaProperties.buildProducerProperties()
        return KafkaProducer(senderProps)
    }

    @Test
    fun `Dødshendelse skal prosesseres uten feil`() {
        val personhendelse = GenericRecordBuilder(Personhendelse.`SCHEMA$`)
        personhendelse.set("hendelseId", "1")
        val personidenter = ArrayList<String>()
        personidenter.add("1234567890123")
        personhendelse.set("personidenter", personidenter)
        personhendelse.set("master", "")
        personhendelse.set("opprettet", 0L)
        personhendelse.set("opplysningstype", "DOEDSFALL_V1")
        personhendelse.set("endringstype", Endringstype.OPPRETTET)

        val dødsfall = GenericRecordBuilder(Doedsfall.`SCHEMA$`)
        dødsfall.set("doedsdato", 1)
        personhendelse.set("doedsfall", dødsfall.build())

        val producer = buildKafkaProducer()
        producer.send(ProducerRecord("aapen-person-pdl-leesah-v1", personhendelse.build()))
        Thread.sleep(1000)
    }

    @Test
    fun `Fødselshendelse skal prosesseres uten feil`() {
        val personhendelse = GenericRecordBuilder(Personhendelse.`SCHEMA$`)
        personhendelse.set("hendelseId", "2")
        val personidenter = ArrayList<String>()
        personidenter.add("1234567890123")
        personidenter.add("12345678901")
        personhendelse.set("personidenter", personidenter)
        personhendelse.set("master", "")
        personhendelse.set("opprettet", 0L)
        personhendelse.set("opplysningstype", "FOEDSEL_V1")
        personhendelse.set("endringstype", Endringstype.OPPRETTET)

        val fødsel = GenericRecordBuilder(Foedsel.`SCHEMA$`)
        fødsel.set("foedselsdato", (Instant.now().toEpochMilli() / (1000 * 3600 * 24)).toInt()) //Setter dagens dato på avroformat

        personhendelse.set("foedsel", fødsel.build())

        val producer = buildKafkaProducer()
        producer.send(ProducerRecord("aapen-person-pdl-leesah-v1", personhendelse.build()))

        var fantTask = false
        for (i in 1..30) {
            if (taskRepository.count() > 0) {
                fantTask = true
                break
            } else {
                Thread.sleep(1000)
            }
        }
        assertThat(fantTask).isTrue() // Det skal finnes en task i taskrepositoriet

        val fødselshendelsetasks =
                restTaskService.hentTasks(listOf(Status.UBEHANDLET), "", 0).data!!.filter { it.taskStepType == "mottaFødselshendelse" }
        assertThat(fødselshendelsetasks).isNotEmpty // Den skal være tilgjengelig via RestTaskService

        assertThat(hendelsesloggRepository.existsByHendelseIdAndConsumer("2",
                                                                         HendelseConsumer.PDL)).isTrue() // Hendelsen skal ha blitt lagret.
    }


    @Test
    fun `Skal lese journalfoeringshendelser`() {
        val journalHendelse = GenericRecordBuilder(JournalfoeringHendelseRecord.`SCHEMA$`)
        journalHendelse.set("hendelsesId", "100")
        journalHendelse.set("versjon", 1)
        journalHendelse.set("hendelsesType", "MidlertidigJournalført")
        journalHendelse.set("journalpostId", 123L)
        journalHendelse.set("journalpostStatus", "M")
        journalHendelse.set("temaGammelt", "BAR")
        journalHendelse.set("temaNytt", "BAR")
        journalHendelse.set("mottaksKanal", "NAV_NO")
        journalHendelse.set("kanalReferanseId", "callId")
        val producer = buildKafkaProducer()

        producer.send(ProducerRecord("aapen-dok-journalfoering-v1-integrasjonstest", journalHendelse.build()))

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted() {
                    assertThat(hendelsesloggRepository.existsByHendelseIdAndConsumer("100",
                                                                                     HendelseConsumer.JOURNAL)).isTrue()
                }
    }

}

class CustomKafkaAvroSerializer : KafkaAvroSerializer {
    constructor() : super() {
        super.schemaRegistry = MockSchemaRegistryClient()
    }

    constructor(client: SchemaRegistryClient?) : super(MockSchemaRegistryClient())
    constructor(client: SchemaRegistryClient?, props: Map<String?, *>?) : super(MockSchemaRegistryClient(), props)
}

class CustomKafkaAvroDeserializer : KafkaAvroDeserializer() {
    override fun deserialize(topic: String?, bytes: ByteArray): Any {
        if (topic.equals("aapen-person-pdl-leesah-v1")) {
            this.schemaRegistry = getMockClient(Personhendelse.`SCHEMA$`)
        } else if (topic.equals("aapen-dok-journalfoering-v1-integrasjonstest")) {
            this.schemaRegistry = getMockClient(JournalfoeringHendelseRecord.`SCHEMA$`)
        }


        return super.deserialize(topic, bytes)
    }
}

private fun getMockClient(schema: Schema): SchemaRegistryClient? {
    return object : MockSchemaRegistryClient() {
        @Synchronized
        override fun getById(id: Int): Schema {
            return schema
        }
    }
}