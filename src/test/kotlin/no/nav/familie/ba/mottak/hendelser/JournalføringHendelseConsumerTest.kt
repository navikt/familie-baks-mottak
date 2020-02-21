package no.nav.familie.ba.mottak.hendelser

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.ba.mottak.task.OpprettOppgaveForJournalføringTask
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
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

    @MockK
    lateinit var mockJournalpostClient: JournalpostClient

    @MockK(relaxed = true)
    lateinit var mockTaskRepository: TaskRepository

    @MockK(relaxed = true)
    lateinit var ack: Acknowledgment


    @InjectMockKs
    lateinit var consumer: JournalføringHendelseConsumer

    @BeforeEach
    internal fun setUp() {
        MockKAnnotations.init(this)
        clearAllMocks()

        //Inngående papirsøknad, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_PAPIRSØKNAD)
        } returns Journalpost(journalpostId = JOURNALPOST_PAPIRSØKNAD,
                              journalposttype = Journalposttype.I,
                              journalstatus = Journalstatus.MOTTATT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "BAR",
                              kanal = "SKAN_NETS",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null)

        //Inngående digital, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_DIGITALSØKNAD)
        } returns Journalpost(journalpostId = JOURNALPOST_DIGITALSØKNAD,
                              journalposttype = Journalposttype.I,
                              journalstatus = Journalstatus.MOTTATT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "BAR",
                              kanal = "NAV_NO",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null)

        //Utgående digital, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_UTGÅENDE_DOKUMENT)
        } returns Journalpost(journalpostId = JOURNALPOST_UTGÅENDE_DOKUMENT,
                              journalposttype = Journalposttype.U,
                              journalstatus = Journalstatus.MOTTATT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "BAR",
                              kanal = "SKAN_NETS",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null)

        //Ikke barnetrygd
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_IKKE_BARNETRYGD)
        } returns Journalpost(journalpostId = JOURNALPOST_IKKE_BARNETRYGD,
                              journalposttype = Journalposttype.U,
                              journalstatus = Journalstatus.MOTTATT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "FOR",
                              kanal = "NAV_NO",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null)

        //ferdigstilt journalpost
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_FERDIGSTILT)
        } returns Journalpost(journalpostId = JOURNALPOST_FERDIGSTILT,
                              journalposttype = Journalposttype.U,
                              journalstatus = Journalstatus.FERDIGSTILT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "FOR",
                              kanal = "NAV_NO",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null)
    }

    @Test
    fun `Mottak av papirsøknader skal opprette OpprettOppgaveForJournalføringTask`() {
        var record = opprettRecord(JOURNALPOST_PAPIRSØKNAD)

        val consumerRecord = ConsumerRecord("topic", 1, 1, 1, record)

        consumer.listen(consumerRecord, ack)


        val taskSlot = slot<Task>()
        verify {
            mockTaskRepository.save(capture(taskSlot))
        }

        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload).isEqualTo(JOURNALPOST_PAPIRSØKNAD)
        assertThat(taskSlot.captured.metadata.getProperty("callId")).isEqualTo("kanalReferanseId")
        assertThat(taskSlot.captured.taskStepType).isEqualTo(OpprettOppgaveForJournalføringTask.TASK_STEP_TYPE)
        verify { ack.acknowledge() }
        verify(exactly = 1) { mockHendelsesloggRepository.save(any()) }
    }


    @Test
    fun `Mottak av digital søknader skal opprette task`() {
        var record = opprettRecord(JOURNALPOST_DIGITALSØKNAD)

        val consumerRecord = ConsumerRecord("topic", 1, 1, 1, record)

        consumer.listen(consumerRecord, ack)

        //TODO kan kommenteres inn når man får OpprettBehandleSakOppgaveTask
//        val taskSlot = slot<Task>()
//        verify {
//            mockTaskRepository.save(capture(taskSlot))
//        }
//
//        assertThat(taskSlot.captured).isNotNull
//        assertThat(taskSlot.captured.payload).isEqualTo(JOURNALPOST_ID_DIGITALSØKNAD)
//        assertThat(taskSlot.captured.metadata.getProperty("callId")).isEqualTo("kanalReferanseId")
//        assertThat(taskSlot.captured.taskStepType).isEqualTo(OpprettBehandleSakOppgaveTask.TASK_STEP_TYPE)
//        verify { ack.acknowledge() }


        verify { ack.acknowledge() }
        verify(exactly = 1) { mockHendelsesloggRepository.save(any()) }
    }

    @Test
    fun `Ikke gyldige hendelsetyper skal ignoreres`() {
        var record = opprettRecord(JOURNALPOST_PAPIRSØKNAD, "UgyldigType")

        val consumerRecord = ConsumerRecord("topic", 1, 1, 1, record)

        consumer.listen(consumerRecord, ack)


        verify(exactly = 0) {
            mockTaskRepository.save(any())
        }

        verify { ack.acknowledge() }
        verify(exactly = 1) { mockHendelsesloggRepository.save(any()) }
    }

    @Test
    fun `Hendelser hvor journalpost ikke har tema for Barnetrygd skal ignoreres`() {
        var record = opprettRecord(JOURNALPOST_IKKE_BARNETRYGD)

        val consumerRecord = ConsumerRecord("topic", 1, 1, 1, record)

        consumer.listen(consumerRecord, ack)


        verify(exactly = 0) {
            mockTaskRepository.save(any())
        }

        verify { ack.acknowledge() }
        verify(exactly = 1) { mockHendelsesloggRepository.save(any()) }
    }

    @Test
    fun `Hendelser hvor journalpost er alt FERDIGSTILT skal ignoreres`() {
        var record = opprettRecord(JOURNALPOST_FERDIGSTILT)

        val consumerRecord = ConsumerRecord("topic", 1, 1, 1, record)

        consumer.listen(consumerRecord, ack)


        verify(exactly = 0) {
            mockTaskRepository.save(any())
        }

        verify { ack.acknowledge() }
        verify(exactly = 1) { mockHendelsesloggRepository.save(any()) }
    }

    @Test
    fun `Utgående journalposter skal ignoreres`() {
        var record = opprettRecord(JOURNALPOST_UTGÅENDE_DOKUMENT)

        val consumerRecord = ConsumerRecord("topic", 1, 1, 1, record)

        consumer.listen(consumerRecord, ack)


        verify(exactly = 0) {
            mockTaskRepository.save(any())
        }

        verify { ack.acknowledge() }
        verify(exactly = 1) { mockHendelsesloggRepository.save(any()) }
    }


    private fun opprettRecord(journalpostId: String,
                              hendelseType: String = "MidlertidigJournalført"): JournalfoeringHendelseRecord {
        var record = JournalfoeringHendelseRecord("hendelseId",
                                                  1,
                                                  hendelseType,
                                                  journalpostId.toLong(),
                                                  "M",
                                                  "BAR",
                                                  "BAR",
                                                  "SKAN_NETS",
                                                  "kanalReferanseId",
                                                  "BAR")
        return record
    }

    companion object {
        const val JOURNALPOST_PAPIRSØKNAD = "111"
        const val JOURNALPOST_DIGITALSØKNAD = "222"
        const val JOURNALPOST_UTGÅENDE_DOKUMENT = "333"
        const val JOURNALPOST_IKKE_BARNETRYGD = "444"
        const val JOURNALPOST_FERDIGSTILT = "555"
    }
}