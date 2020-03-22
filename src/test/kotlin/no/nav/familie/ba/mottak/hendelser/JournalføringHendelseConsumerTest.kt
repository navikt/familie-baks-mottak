package no.nav.familie.ba.mottak.hendelser

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.Hendelseslogg
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.kafka.support.Acknowledgment

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JournalføringHendelseConsumerTest {

    @MockK(relaxed = true)
    lateinit var mockHendelsesloggRepository: HendelsesloggRepository


    @MockK(relaxed = true)
    lateinit var mockJournalhendelseService: JournalhendelseService

    @MockK(relaxed = true)
    lateinit var ack: Acknowledgment


    @InjectMockKs
    lateinit var consumer: JournalføringHendelseConsumer

    @BeforeEach
    internal fun setUp() {
        MockKAnnotations.init(this)
        clearAllMocks()
    }

    @Test
    fun `Skal ignorere hendelse fordi den eksisterer i hendelseslogg`() {
        val consumerRecord = ConsumerRecord("topic", 1, OFFSET, HENDELSE_ID, opprettRecord())
        every {
            mockHendelsesloggRepository.existsByHendelseIdAndConsumer(HENDELSE_ID.toString(), HendelseConsumer.JOURNAL)
        } returns true

        consumer.listen(consumerRecord, ack)

        verify(exactly = 0) {
            mockJournalhendelseService.behandleJournalhendelse(any())
        }
        verify { ack.acknowledge() }

        verify(exactly = 0) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Mottak av gyldig hendelse skal delegeres til service`() {
        val consumerRecord = ConsumerRecord("topic", 1, OFFSET, HENDELSE_ID, opprettRecord())

        consumer.listen(consumerRecord, ack)

        verify {
            mockJournalhendelseService.behandleJournalhendelse(any())
        }
        verify { ack.acknowledge() }

        val slot = slot<Hendelseslogg>()
        verify(exactly = 1) {
            mockHendelsesloggRepository.save(capture(slot))
        }

        assertThat(slot.captured).isNotNull
        assertThat(slot.captured.offset).isEqualTo(OFFSET)
        assertThat(slot.captured.hendelseId).isEqualTo(HENDELSE_ID.toString())
        assertThat(slot.captured.consumer).isEqualTo(HendelseConsumer.JOURNAL)
        assertThat(slot.captured.metadata["journalpostId"]).isEqualTo(JOURNALPOST_ID)
        assertThat(slot.captured.metadata["hendelsesType"]).isEqualTo("MidlertidigJournalført")
    }


    @Test
    fun `Ikke gyldige hendelsetyper skal ignoreres`() {
        val ugyldigHendelsetypeRecord = opprettRecord(hendelseType = "UgyldigType")
        val consumerRecord = ConsumerRecord("topic", 1, OFFSET, HENDELSE_ID, ugyldigHendelsetypeRecord)

        consumer.listen(consumerRecord, ack)


        verify(exactly = 0) {
            mockJournalhendelseService.behandleJournalhendelse(any())
        }
        verify { ack.acknowledge() }
        val slot = slot<Hendelseslogg>()
        verify(exactly = 1) {
            mockHendelsesloggRepository.save(capture(slot))
        }
        assertThat(slot.captured).isNotNull
        assertThat(slot.captured.offset).isEqualTo(OFFSET)
        assertThat(slot.captured.hendelseId).isEqualTo(HENDELSE_ID.toString())
        assertThat(slot.captured.consumer).isEqualTo(HendelseConsumer.JOURNAL)
        assertThat(slot.captured.metadata["journalpostId"]).isEqualTo(JOURNALPOST_ID)
        assertThat(slot.captured.metadata["hendelsesType"]).isEqualTo("UgyldigType")
    }

    @Test
    fun `Hendelser hvor journalpost ikke har tema for Barnetrygd skal ignoreres`() {
        val ukjentTemaRecord = opprettRecord(temaNytt = "UKJ")

        val consumerRecord = ConsumerRecord("topic", 1, OFFSET, HENDELSE_ID, ukjentTemaRecord)

        consumer.listen(consumerRecord, ack)


        verify(exactly = 0) {
            mockJournalhendelseService.behandleJournalhendelse(any())
        }

        verify { ack.acknowledge() }
        val slot = slot<Hendelseslogg>()
        verify(exactly = 1) {
            mockHendelsesloggRepository.save(capture(slot))
        }
        assertThat(slot.captured).isNotNull
        assertThat(slot.captured.offset).isEqualTo(OFFSET)
        assertThat(slot.captured.hendelseId).isEqualTo(HENDELSE_ID.toString())
        assertThat(slot.captured.consumer).isEqualTo(HendelseConsumer.JOURNAL)
        assertThat(slot.captured.metadata["journalpostId"]).isEqualTo(JOURNALPOST_ID)
        assertThat(slot.captured.metadata["hendelsesType"]).isEqualTo("MidlertidigJournalført")
    }


    private fun opprettRecord(temaNytt: String = "BAR",
                              hendelseType: String = "MidlertidigJournalført"): JournalfoeringHendelseRecord {
        return JournalfoeringHendelseRecord(HENDELSE_ID.toString(),
                                            1,
                                            hendelseType,
                                            JOURNALPOST_ID.toLong(),
                                            "M",
                                            "BAR",
                                            temaNytt,
                                            "SKAN_NETS",
                                            "kanalReferanseId",
                                            "BAR")
    }

    companion object {
        const val OFFSET = 21L
        const val HENDELSE_ID = 42L
        const val JOURNALPOST_ID = "1"


    }
}