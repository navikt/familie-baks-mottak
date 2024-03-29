package no.nav.familie.baks.mottak.hendelser

import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.baks.mottak.domene.HendelsesloggRepository
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.kafka.support.Acknowledgment

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnsligForsørgerVedtakHendelseConsumerTest {
    lateinit var mockHendelsesloggRepository: HendelsesloggRepository
    lateinit var mockSakClient: BaSakClient
    lateinit var mockPdlClient: PdlClient
    lateinit var service: EnsligForsørgerHendelseService

    lateinit var consumer: EnsligForsørgerVedtakHendelseConsumer

    @BeforeEach
    internal fun setUp() {
        mockHendelsesloggRepository = mockk(relaxed = true)
        mockSakClient = mockk(relaxed = true)
        mockPdlClient = mockk(relaxed = true)
        service = EnsligForsørgerHendelseService(mockSakClient, mockPdlClient, mockHendelsesloggRepository)
        consumer = EnsligForsørgerVedtakHendelseConsumer(service)
        clearAllMocks()
    }

    @Test
    fun `Skal lese melding, konvertere, sende til ba-sak og ACKe melding `() {
        val ack: Acknowledgment = mockk(relaxed = true)
        val consumerRecord = ConsumerRecord("topic", 1, 1, "42", """{"behandlingId":42,"personIdent":"12345678910","stønadType":"OVERGANGSSTØNAD"}""")
        consumer.listen(consumerRecord, ack)
        verify(exactly = 1) {
            mockSakClient.sendVedtakOmOvergangsstønadHendelseTilBaSak("12345678910")
        }
        verify(exactly = 1) { ack.acknowledge() }
    }

    @Test
    fun `Skal ikke ACKe melding ved feil`() {
        val ack: Acknowledgment = mockk(relaxed = true)
        val consumerRecord = ConsumerRecord("topic", 1, 1, "42", """{"json": "Ugyldig"}""")
        val e =
            Assertions.assertThrows(RuntimeException::class.java) {
                consumer.listen(consumerRecord, ack)
            }

        assertThat(e.message).isEqualTo("Feil i prosessering av aapen-ensligforsorger-iverksatt-vedtak")

        verify(exactly = 0) { ack.acknowledge() }
    }
}
