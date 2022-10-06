package no.nav.familie.ba.mottak.hendelser

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.integrasjoner.PdlClient
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.felles.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.kafka.support.Acknowledgment

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnsligForsørgerInfotrygdHendelseConsumerTest {

    lateinit var mockHendelsesloggRepository: HendelsesloggRepository
    lateinit var mockSakClient: SakClient
    lateinit var mockPdlClient: PdlClient
    lateinit var service: EnsligForsørgerHendelseService

    lateinit var consumer: EnsligForsørgerInfotrygdHendelseConsumer

    private val json = """{
    "table": "INFOTRYGD_Q1.T_HENDELSE",
    "op_type": "I",
    "op_ts": "2021-11-11 17:49:51.000000",
    "current_ts": "2021-11-11T17:49:55.821000",
    "pos": "00000000300000004241",
    "after": {
        "HENDELSE_ID": 10343774,
        "TYPE_HENDELSE": "INNVILGET      ",
        "AKTOR_ID": "2424242424241       ",
        "TYPE_YTELSE": "EF",
        "IDENTDATO": "20210801",
        "FOM": "2021-08-01 00:00:00",
        "SATS": 8820.00,
        "KOBLING_ID": 0,
        "BRUKERID": "K278CP10",
        "TIDSPUNKT_REG": "2021-11-11 17:49:36.690083000",
        "OPPRETTET": "2021-11-11 17:49:44.361512000",
        "OPPDATERT": "2021-11-11 17:49:44.361512000",
        "DB_SPLITT": "  "
    }}"""

    @BeforeEach
    internal fun setUp() {
        mockHendelsesloggRepository = mockk(relaxed = true)
        mockSakClient = mockk(relaxed = true)
        mockPdlClient = mockk(relaxed = true)
        service = EnsligForsørgerHendelseService(mockSakClient, mockPdlClient, mockHendelsesloggRepository)
        consumer = EnsligForsørgerInfotrygdHendelseConsumer(service)
        clearAllMocks()

        every { mockPdlClient.hentPersonident("2424242424241") } returns "12345678910"
    }

    @Test
    fun `Konverter json til dto`() {
        val hendelseDto = objectMapper.readValue(json, EnsligForsørgerInfotrygdHendelse::class.java).after
        assertThat(hendelseDto.hendelseId).isEqualTo("10343774")
        assertThat(hendelseDto.typeHendelse).contains("INNVILGET")
        assertThat(hendelseDto.fom).isEqualTo("2021-08-01 00:00:00")
        assertThat(hendelseDto.sats).isEqualTo(8820.0)
        assertThat(hendelseDto.typeYtelse).isEqualTo("EF")
        assertThat(hendelseDto.koblingId).isEqualTo(0)
        assertThat(hendelseDto.identdato).isEqualTo("20210801")

        assertThat(hendelseDto.aktørId).isEqualTo(2424242424241)
    }

    @Test
    @Disabled // FIXME, enables når vi merger til master med hendelser på
    fun `Skal lese melding, konvertere, sende til ba-sak og ACKe melding `() {
        val ack: Acknowledgment = mockk(relaxed = true)
        val consumerRecord = ConsumerRecord("topic", 1, 1, "42", json)
        consumer.listen(consumerRecord, ack)
        verify(exactly = 1) {
            mockSakClient.sendVedtakOmOvergangsstønadHendelseTilSak("12345678910")
        }
        verify(exactly = 1) { ack.acknowledge() }
    }

    @Test
    fun `Skal ikke ACKe melding ved feil`() {
        val ack: Acknowledgment = mockk(relaxed = true)
        val consumerRecord = ConsumerRecord("topic", 1, 1, "42", """{"json": "Ugyldig"}""")
        val e = Assertions.assertThrows(RuntimeException::class.java) {
            consumer.listen(consumerRecord, ack)
        }
        assertThat(e.message).isEqualTo("Feil i prosessering av aapen-ef-overgangstonad-v1")

        verify(exactly = 0) { ack.acknowledge() }
    }
}
