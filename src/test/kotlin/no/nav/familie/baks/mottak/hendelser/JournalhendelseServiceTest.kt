package no.nav.familie.baks.mottak.hendelser

import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.domene.HendelseConsumer
import no.nav.familie.baks.mottak.domene.Hendelseslogg
import no.nav.familie.baks.mottak.domene.HendelsesloggRepository
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus.LØPENDE
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdBarnetrygdClient
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClientService
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.baks.mottak.task.JournalhendelseBarnetrygdRutingTask
import no.nav.familie.baks.mottak.task.OpprettJournalføringOppgaveTask
import no.nav.familie.baks.mottak.task.SendTilBaSakTask
import no.nav.familie.kontrakter.ba.infotrygd.InfotrygdSøkResponse
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.MDC
import org.springframework.kafka.support.Acknowledgment

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JournalhendelseServiceTest {
    @MockK
    lateinit var mockJournalpostClient: JournalpostClient

    @MockK(relaxed = true)
    lateinit var mockOppgaveClient: OppgaveClientService

    @MockK
    lateinit var baSakClient: BaSakClient

    @MockK
    lateinit var infotrygdBarnetrygdClient: InfotrygdBarnetrygdClient

    @MockK
    lateinit var pdlClient: PdlClient

    @MockK(relaxed = true)
    lateinit var mockTaskService: TaskService

    @MockK(relaxed = true)
    lateinit var mockHendelsesloggRepository: HendelsesloggRepository

    @MockK(relaxed = true)
    lateinit var ack: Acknowledgment

    @InjectMockKs
    lateinit var service: JournalhendelseService

    @BeforeEach
    internal fun setUp() {
        MockKAnnotations.init(this)
        clearAllMocks()

        // Inngående papirsøknad, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_PAPIRSØKNAD)
        } returns
            Journalpost(
                journalpostId = JOURNALPOST_PAPIRSØKNAD,
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                tema = "BAR",
                kanal = "SKAN_NETS",
                behandlingstema = null,
                dokumenter = null,
                journalforendeEnhet = null,
                sak = null,
            )

        // Inngående digital, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_DIGITALSØKNAD)
        } returns
            Journalpost(
                journalpostId = JOURNALPOST_DIGITALSØKNAD,
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                tema = "BAR",
                kanal = "NAV_NO",
                behandlingstema = null,
                dokumenter = null,
                journalforendeEnhet = null,
                sak = null,
            )

        // Utgående digital, Mottatt
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_UTGÅENDE_DOKUMENT)
        } returns
            Journalpost(
                journalpostId = JOURNALPOST_UTGÅENDE_DOKUMENT,
                journalposttype = Journalposttype.U,
                journalstatus = Journalstatus.MOTTATT,
                bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                tema = "BAR",
                kanal = "SKAN_NETS",
                behandlingstema = null,
                dokumenter = null,
                journalforendeEnhet = null,
                sak = null,
            )

        // Ikke barnetrygd
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_IKKE_BARNETRYGD)
        } returns
            Journalpost(
                journalpostId = JOURNALPOST_IKKE_BARNETRYGD,
                journalposttype = Journalposttype.U,
                journalstatus = Journalstatus.MOTTATT,
                bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                tema = "FOR",
                kanal = "NAV_NO",
                behandlingstema = null,
                dokumenter = null,
                journalforendeEnhet = null,
                sak = null,
            )

        // ferdigstilt journalpost
        every {
            mockJournalpostClient.hentJournalpost(JOURNALPOST_FERDIGSTILT)
        } returns
            Journalpost(
                journalpostId = JOURNALPOST_FERDIGSTILT,
                journalposttype = Journalposttype.U,
                journalstatus = Journalstatus.FERDIGSTILT,
                bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                tema = "BAR",
                kanal = "NAV_NO",
                behandlingstema = null,
                dokumenter = null,
                journalforendeEnhet = null,
                sak = null,
            )

        every { mockTaskService.save(any()) } returns Task("dummy", "payload")
    }

    @Test
    fun `Mottak av papirsøknader skal opprette JournalhendelseRutingTask`() {
        MDC.put("callId", "papir")
        val record = opprettRecord(JOURNALPOST_PAPIRSØKNAD)

        service.behandleJournalhendelse(record)

        val taskSlot = slot<Task>()
        verify {
            mockTaskService.save(capture(taskSlot))
        }

        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload).isEqualTo("SKAN_NETS")
        assertThat(taskSlot.captured.metadata.getProperty("callId")).isEqualTo("papir")
        assertThat(taskSlot.captured.type).isEqualTo(JournalhendelseBarnetrygdRutingTask.TASK_STEP_TYPE)
    }

    @Test
    fun `Mottak av digital søknader skal opprette task`() {
        MDC.put("callId", "digital")
        val record = opprettRecord(JOURNALPOST_DIGITALSØKNAD)

        service.behandleJournalhendelse(record)

        val taskSlot = slot<Task>()
        verify {
            mockTaskService.save(capture(taskSlot))
        }

        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload).isEqualTo("NAV_NO")
        assertThat(taskSlot.captured.metadata.getProperty("callId")).isEqualTo("digital")
        assertThat(taskSlot.captured.type).isEqualTo(JournalhendelseBarnetrygdRutingTask.TASK_STEP_TYPE)
    }

    @Test
    fun `Hendelser hvor journalpost er alt FERDIGSTILT skal ignoreres`() {
        val record = opprettRecord(JOURNALPOST_FERDIGSTILT)

        service.behandleJournalhendelse(record)

        verify(exactly = 0) {
            mockTaskService.save(any())
        }
    }

    @Test
    fun `Utgående journalposter skal ignoreres`() {
        val record = opprettRecord(JOURNALPOST_UTGÅENDE_DOKUMENT)

        service.behandleJournalhendelse(record)

        verify(exactly = 0) {
            mockTaskService.save(any())
        }
    }

    @Test
    fun `Oppretter oppgave dersom det ikke eksisterer en allerede`() {
        every {
            mockOppgaveClient.finnOppgaver(any(), any())
        } returns listOf()

        val sakssystemMarkering = slot<String>()
        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), capture(sakssystemMarkering))
        } returns OppgaveResponse(1)

        justRun {
            mockTaskService.save(any<Task>())
        }

        every {
            pdlClient.hentPersonident(any(), any())
        } returns "12345678910"

        every {
            baSakClient.hentRestFagsakDeltagerListe(any(), emptyList())
        } returns listOf(RestFagsakDeltager("12345678910", FORELDER, 1L, LØPENDE))

        every {
            infotrygdBarnetrygdClient.hentLøpendeUtbetalinger(any(), any())
        } returns InfotrygdSøkResponse(emptyList(), emptyList())

        every {
            infotrygdBarnetrygdClient.hentSaker(any(), any())
        } returns InfotrygdSøkResponse(emptyList(), emptyList())

        val task =
            OpprettJournalføringOppgaveTask(
                mockJournalpostClient,
                mockOppgaveClient,
            )

        task.doTask(
            Task(type = SendTilBaSakTask.TASK_STEP_TYPE, payload = "oppgavebeskrivelse").apply {
                this.metadata["journalpostId"] = JOURNALPOST_UTGÅENDE_DOKUMENT
            },
        )
        assertThat(sakssystemMarkering.captured).contains("oppgavebeskrivelse")
    }

    @Test
    fun `Oppretter ikke oppgave dersom det eksisterer en allerede`() {
        every {
            mockOppgaveClient.finnOppgaver(any(), Oppgavetype.Journalføring)
        } returns listOf()
        every {
            mockOppgaveClient.finnOppgaver(JOURNALPOST_UTGÅENDE_DOKUMENT, Oppgavetype.Journalføring)
        } returns listOf(Oppgave(123))
        every {
            mockOppgaveClient.finnOppgaver(JOURNALPOST_PAPIRSØKNAD, Oppgavetype.Fordeling)
        } returns listOf(Oppgave(123))

        val task =
            OpprettJournalføringOppgaveTask(
                mockJournalpostClient,
                mockOppgaveClient,
            )
        task.doTask(
            Task(type = SendTilBaSakTask.TASK_STEP_TYPE, payload = JOURNALPOST_UTGÅENDE_DOKUMENT).apply {
                this.metadata["journalpostId"] = JOURNALPOST_UTGÅENDE_DOKUMENT
            },
        )
        task.doTask(
            Task(SendTilBaSakTask.TASK_STEP_TYPE, JOURNALPOST_PAPIRSØKNAD).apply {
                this.metadata["journalpostId"] = JOURNALPOST_PAPIRSØKNAD
            },
        )

        verify(exactly = 0) {
            mockTaskService.save(any<Task>())
        }
    }

    @Test
    fun `Kaster exception dersom journalstatus annet enn MOTTATT`() {
        val task =
            OpprettJournalføringOppgaveTask(
                mockJournalpostClient,
                mockOppgaveClient,
            )

        Assertions.assertThrows(IllegalStateException::class.java) {
            task.doTask(
                Task(SendTilBaSakTask.TASK_STEP_TYPE, JOURNALPOST_FERDIGSTILT).apply {
                    this.metadata["journalpostId"] = JOURNALPOST_FERDIGSTILT
                },
            )
        }
    }

    @Test
    fun `Skal ignorere hendelse fordi den eksisterer i hendelseslogg`() {
        val consumerRecord =
            ConsumerRecord(
                "topic",
                1,
                OFFSET,
                42L,
                opprettRecord(JOURNALPOST_PAPIRSØKNAD),
            )
        every {
            mockHendelsesloggRepository.existsByHendelseIdAndConsumer("hendelseId", HendelseConsumer.JOURNAL_AIVEN)
        } returns true

        service.prosesserNyHendelse(consumerRecord, ack)

        verify { ack.acknowledge() }

        verify(exactly = 0) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Mottak av gyldig hendelse skal delegeres til service`() {
        val consumerRecord =
            ConsumerRecord(
                "topic",
                1,
                OFFSET,
                42L,
                opprettRecord(JOURNALPOST_PAPIRSØKNAD),
            )

        service.prosesserNyHendelse(consumerRecord, ack)

        verify { ack.acknowledge() }

        val slot = slot<Hendelseslogg>()
        verify(exactly = 1) {
            mockHendelsesloggRepository.save(capture(slot))
        }

        assertThat(slot.captured).isNotNull
        assertThat(slot.captured.offset).isEqualTo(OFFSET)
        assertThat(slot.captured.hendelseId).isEqualTo(HENDELSE_ID)
        assertThat(slot.captured.consumer).isEqualTo(HendelseConsumer.JOURNAL_AIVEN)
        assertThat(slot.captured.metadata["journalpostId"]).isEqualTo(JOURNALPOST_PAPIRSØKNAD)
        assertThat(slot.captured.metadata["hendelsesType"]).isEqualTo("JournalpostMottatt")
    }

    @Test
    fun `Ikke gyldige hendelsetyper skal ignoreres`() {
        val ugyldigHendelsetypeRecord =
            opprettRecord(journalpostId = JOURNALPOST_PAPIRSØKNAD, hendelseType = "UgyldigType", temaNytt = "BAR")
        val consumerRecord =
            ConsumerRecord(
                "topic",
                1,
                OFFSET,
                42L,
                ugyldigHendelsetypeRecord,
            )

        service.prosesserNyHendelse(consumerRecord, ack)

        verify { ack.acknowledge() }
        verify(exactly = 0) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Hendelser hvor journalpost ikke har tema for Barnetrygd skal ignoreres`() {
        val ukjentTemaRecord = opprettRecord(journalpostId = JOURNALPOST_PAPIRSØKNAD, temaNytt = "UKJ")

        val consumerRecord =
            ConsumerRecord(
                "topic",
                1,
                OFFSET,
                42L,
                ukjentTemaRecord,
            )

        service.prosesserNyHendelse(consumerRecord, ack)

        verify { ack.acknowledge() }
        verify(exactly = 0) {
            mockHendelsesloggRepository.save(any())
        }
    }

    private fun opprettRecord(
        journalpostId: String,
        hendelseType: String = "JournalpostMottatt",
        temaNytt: String = "BAR",
    ): JournalfoeringHendelseRecord =
        JournalfoeringHendelseRecord(
            HENDELSE_ID,
            1,
            hendelseType,
            journalpostId.toLong(),
            "M",
            "BAR",
            temaNytt,
            "SKAN_NETS",
            "kanalReferanseId",
            "BAR",
        )

    companion object {
        const val JOURNALPOST_PAPIRSØKNAD = "111"
        const val JOURNALPOST_DIGITALSØKNAD = "222"
        const val JOURNALPOST_UTGÅENDE_DOKUMENT = "333"
        const val JOURNALPOST_IKKE_BARNETRYGD = "444"
        const val JOURNALPOST_FERDIGSTILT = "555"
        const val OFFSET = 21L
        const val HENDELSE_ID = "hendelseId"
    }
}
