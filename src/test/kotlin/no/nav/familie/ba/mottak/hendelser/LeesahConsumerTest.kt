package no.nav.familie.ba.mottak.hendelser

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.util.DbContainerInitializer
import no.nav.familie.prosessering.domene.TaskRepository
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
import java.time.LocalDate

@SpringBootTest(properties = ["spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}"])
@ExtendWith(SpringExtension::class)
@ContextConfiguration(initializers = [DbContainerInitializer::class])
@ActiveProfiles("integrasjonstest")
@EmbeddedKafka(partitions = 1, topics = ["aapen-person-pdl-leesah-v1"])
@Tag("integration")
class LeesahConsumerTest {
    @Autowired
    lateinit var hendelsesloggRepository: HendelsesloggRepository

    @Autowired
    lateinit var kafkaProperties: KafkaProperties

    @Autowired
    lateinit var taskRepository: TaskRepository

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
        for (i in  1..30) {
            if (taskRepository.count() > 0) {
                fantTask = true
                break
            } else {
                Thread.sleep(1000)
            }
        }

        assertThat(fantTask).isTrue() // Tester at fødselshendelsen generer en task.
        assertThat(hendelsesloggRepository.existsByHendelseId("2")).isTrue() // Tester at vi får logget hendelsesIden som brukes i idempotenssjekken.
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
    override fun deserialize(topic: String?, bytes: ByteArray) : Any {
        if (topic.equals("aapen-person-pdl-leesah-v1")) {
            this.schemaRegistry = getMockClient(Personhendelse.`SCHEMA$`)
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