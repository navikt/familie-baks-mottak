package no.nav.familie.ba.mottak.hendelser

import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.util.DbContainerInitializer
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericRecord
import org.apache.avro.generic.GenericRecordBuilder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.concurrent.TimeUnit


@SpringBootTest(properties = ["spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}"])
@ExtendWith(SpringExtension::class)
@ContextConfiguration(initializers = [DbContainerInitializer::class])
@ActiveProfiles("postgres")
@EmbeddedKafka(
        // We're only needing to test Kafka serializing interactions, so keep partitioning simple
        partitions = 1,
        // use some non-default topics to test via
        topics = ["aapen-person-pdl-leesah-v1"]
        )
@Tag("integration")
class LeesahConsumerTest {

    @Autowired
    lateinit var hendelsesloggRepository: HendelsesloggRepository

    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker


    private var producer: KafkaTemplate<String, GenericRecord>? = null




    private fun buildKafkaTemplate(): KafkaTemplate<String, GenericRecord>? {
        val senderProps = KafkaTestUtils.producerProps(embeddedKafkaBroker)
        val pf: ProducerFactory<String, GenericRecord> = DefaultKafkaProducerFactory(senderProps)
        return KafkaTemplate(pf)
    }


    @Test
    @Throws(InterruptedException::class)
    fun listenerShouldConsumeMessages() {
        producer = buildKafkaTemplate()
        producer!!.defaultTopic = "aapen-person-pdl-leesah-v1"

        // Given
        var schema = SchemaBuilder.record("hendelse").fields().requiredString("hendelseId").endRecord()
        var genericRecord: GenericRecordBuilder = GenericRecordBuilder(schema)
        genericRecord.set("hendelseId", "123")


        //producer!!.sendDefault("123", null)

    }

}