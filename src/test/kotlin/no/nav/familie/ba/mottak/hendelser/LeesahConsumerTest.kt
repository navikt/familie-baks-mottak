package no.nav.familie.ba.mottak.hendelser

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.util.DbContainerInitializer
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import org.apache.avro.Schema
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericRecord
import org.apache.avro.generic.GenericRecordBuilder
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@SpringBootTest(properties = ["spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}"])
@ExtendWith(SpringExtension::class)
@ContextConfiguration(initializers = [DbContainerInitializer::class])
@ActiveProfiles("postgres")
@EmbeddedKafka(partitions = 1, topics = ["aapen-person-pdl-leesah-v1"])
@Tag("integration")
class LeesahConsumerTest {
    @Autowired
    lateinit var hendelsesloggRepository: HendelsesloggRepository

    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    @Autowired
    lateinit var kafkaProperties: KafkaProperties

    private fun buildKafkaProducer(): Producer<Int, GenericRecord> {
        val senderProps = kafkaProperties.buildProducerProperties()
        return KafkaProducer(senderProps)
    }

    @Test
    @Throws(InterruptedException::class)
    fun listenerShouldConsumeMessages() {
        val producer2 = buildKafkaProducer()

        var genericRecord: GenericRecordBuilder = GenericRecordBuilder(Personhendelse.`SCHEMA$`)
        genericRecord.set("hendelseId", "123")
        val x = ArrayList<String>()
        x.add("asdsa")
        genericRecord.set("personidenter", x)
        genericRecord.set("master", "Geirmund")
        genericRecord.set("opprettet", 123678126L)
        genericRecord.set("opplysningstype", "opplysn")
        genericRecord.set("endringstype", Endringstype.OPPRETTET)

        producer2.send(ProducerRecord("aapen-person-pdl-leesah-v1", genericRecord.build()))

        Thread.sleep(100_000)
    }
}

class CustomKafkaAvroSerializer : KafkaAvroSerializer {
    constructor() : super() {
        super.schemaRegistry = MockSchemaRegistryClient()
    }

    constructor(client: SchemaRegistryClient?) : super(MockSchemaRegistryClient()) {}
    constructor(client: SchemaRegistryClient?, props: Map<String?, *>?) : super(MockSchemaRegistryClient(), props) {}
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

class Hendelse(var hendelseId: String? = "") : SpecificRecord {
    fun getReaderSchema(writerSchema: Schema) : Schema {
        return writerSchema
    }

    override fun put(i: Int, v: Any?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun get(i: Int): Any {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getSchema(): Schema {
        return SchemaBuilder.record("no.nav.familie.ba.mottak.hendelser.Hendelse").fields().requiredString("hendelseId").endRecord()
    }
}