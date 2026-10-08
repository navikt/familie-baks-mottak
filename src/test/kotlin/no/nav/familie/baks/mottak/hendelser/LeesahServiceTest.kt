package no.nav.familie.baks.mottak.hendelser

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.baks.mottak.domene.HendelsesloggRepository
import no.nav.familie.baks.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.baks.mottak.task.MottaAnnullerFødselTask
import no.nav.familie.baks.mottak.task.MottaFødselshendelseTask
import no.nav.familie.baks.mottak.task.VurderBarnetrygdLivshendelseTask
import no.nav.familie.baks.mottak.task.VurderFinnmarkstillleggTaskDTO
import no.nav.familie.baks.mottak.task.VurderKontantstøtteLivshendelseTask
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.byLessThan
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.core.env.Environment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.MINUTES
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextUInt

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LeesahServiceTest {
    lateinit var mockHendelsesloggRepository: HendelsesloggRepository
    lateinit var mockTaskService: TaskService
    lateinit var mockenv: Environment
    lateinit var service: LeesahService
    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    internal fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        Metrics.addRegistry(meterRegistry)
        mockHendelsesloggRepository = mockk(relaxed = true)
        mockTaskService = mockk(relaxed = true)
        mockenv = mockk<Environment>(relaxed = true)
        service = LeesahService(mockHendelsesloggRepository, mockTaskService, 1, mockenv)
        clearAllMocks()
        every {
            mockTaskService.save(any<Task>())
        } returns Task("dummy", "payload")
    }

    @AfterEach
    fun tearDown() {
        Metrics.removeRegistry(meterRegistry)
        meterRegistry.close()
    }

    private fun hendelseTeller(
        opplysningstype: String,
        endringstype: String = "opprettet",
        resultat: String = "task_opprettet",
    ) = Metrics.counter(
        "familie.baks.mottak.leesah.hendelser",
        "opplysningstype",
        opplysningstype,
        "endringstype",
        endringstype,
        "resultat",
        resultat,
    )

    private fun opprettetHendelseTeller(
        opplysningstype: String,
        endringstype: String = "opprettet",
    ) = hendelseTeller(opplysningstype, endringstype)

    private fun hendelse(
        opplysningstype: String,
        endringstype: String = LeesahService.OPPRETTET,
    ) = PdlHendelse(
        offset = 1L,
        gjeldendeAktørId = "1234567890123",
        hendelseId = UUID.randomUUID().toString(),
        personIdenter = listOf("12345678901"),
        opplysningstype = opplysningstype,
        endringstype = endringstype,
        dødsdato = LocalDate.now(),
        fødselsdato = LocalDate.now(),
        tidligereHendelseId = "tidligere",
        sivilstand = "GIFT",
    )

    @ParameterizedTest
    @CsvSource(
        "DOEDSFALL_V1,doedsfall,OPPRETTET,opprettet,2",
        "UTFLYTTING_FRA_NORGE,utflytting,OPPRETTET,opprettet,2",
        "FOEDSELSDATO_V1,foedselsdato,OPPRETTET,opprettet,1",
        "FOEDSELSDATO_V1,foedselsdato,ANNULLERT,annullert,1",
        "SIVILSTAND_V1,sivilstand,OPPRETTET,opprettet,1",
        "BOSTEDSADRESSE_V1,bostedsadresse,KORRIGERT,korrigert,1",
        "OPPHOLDSADRESSE_V1,oppholdsadresse,OPPRETTET,opprettet,1",
        "FALSK_ID_V1,falsk_id,OPPRETTET,opprettet,1",
        "ADRESSEBESKYTTELSE_V1,adressebeskyttelse,ANNULLERT,annullert,1",
    )
    fun `teller en gang per hendelse som oppretter task`(
        opplysningstype: String,
        metrikkType: String,
        endringstype: String,
        metrikkEndring: String,
        antallTasker: Int,
    ) {
        val teller = opprettetHendelseTeller(metrikkType, metrikkEndring)
        val før = teller.count()

        service.prosesserNyHendelse(hendelse(opplysningstype, endringstype))

        assertThat(teller.count()).isEqualTo(før + 1)
        verify(exactly = antallTasker) { mockTaskService.save(any<Task>()) }
    }

    @Test
    fun `teller ikke filtrerte hendelser eller duplikater`() {
        val teller = opprettetHendelseTeller("foedselsdato")
        val før = teller.count()
        service.prosesserNyHendelse(hendelse(LeesahService.OPPLYSNINGSTYPE_FØDSELSDATO).copy(fødselsdato = null))
        val annullert = opprettetHendelseTeller("foedselsdato", "annullert")
        val førAnnullert = annullert.count()
        service.prosesserNyHendelse(hendelse(LeesahService.OPPLYSNINGSTYPE_FØDSELSDATO, LeesahService.ANNULLERT).copy(tidligereHendelseId = null))
        every { mockHendelsesloggRepository.existsByHendelseIdAndConsumer(any(), any()) } returns true
        service.prosesserNyHendelse(hendelse(LeesahService.OPPLYSNINGSTYPE_FØDSELSDATO))

        assertThat(teller.count()).isEqualTo(før)
        assertThat(annullert.count()).isEqualTo(førAnnullert)
        verify(exactly = 0) { mockTaskService.save(any<Task>()) }
    }

    @Test
    fun `felles metrikk beholder fødselstelling før tasklagring og under 18-filteret`() {
        val registrert = hendelseTeller("foedselsdato", resultat = "registrert")
        val ignorertUnder18 = hendelseTeller("foedselsdato", resultat = "ignorert_under_18")
        val førRegistrert = registrert.count()
        val førIgnorert = ignorertUnder18.count()
        every { mockTaskService.save(any<Task>()) } throws RuntimeException("lagring feilet")

        assertThatThrownBy { service.prosesserNyHendelse(hendelse(LeesahService.OPPLYSNINGSTYPE_FØDSELSDATO)) }
            .isInstanceOf(RuntimeException::class.java)
        service.prosesserNyHendelse(hendelse(LeesahService.OPPLYSNINGSTYPE_FØDSELSDATO).copy(fødselsdato = LocalDate.now().minusYears(1)))

        assertThat(registrert.count()).isEqualTo(førRegistrert + 1)
        assertThat(ignorertUnder18.count()).isEqualTo(førIgnorert + 1)
    }

    @Test
    fun `felles metrikk teller annullert fødsel også når referansen mangler`() {
        val registrert = hendelseTeller("foedselsdato", "annullert", "registrert")
        val taskOpprettet = opprettetHendelseTeller("foedselsdato", "annullert")
        val førRegistrert = registrert.count()
        val førTask = taskOpprettet.count()

        service.prosesserNyHendelse(
            hendelse(LeesahService.OPPLYSNINGSTYPE_FØDSELSDATO, LeesahService.ANNULLERT)
                .copy(tidligereHendelseId = null),
        )

        assertThat(registrert.count()).isEqualTo(førRegistrert + 1)
        assertThat(taskOpprettet.count()).isEqualTo(førTask)
        verify(exactly = 0) { mockTaskService.save(any<Task>()) }
    }

    @Test
    fun `felles metrikk teller opprettet sivilstand før filter og ignorert etter filter`() {
        val registrert = hendelseTeller("sivilstand", resultat = "registrert")
        val ignorert = hendelseTeller("sivilstand", resultat = "ignorert")
        val taskOpprettet = opprettetHendelseTeller("sivilstand")
        val førRegistrert = registrert.count()
        val førIgnorert = ignorert.count()
        val førTask = taskOpprettet.count()

        service.prosesserNyHendelse(hendelse(LeesahService.OPPLYSNINGSTYPE_SIVILSTAND).copy(sivilstand = "UOPPGITT"))

        assertThat(registrert.count()).isEqualTo(førRegistrert + 1)
        assertThat(ignorert.count()).isEqualTo(førIgnorert + 1)
        assertThat(taskOpprettet.count()).isEqualTo(førTask)
        verify(exactly = 0) { mockTaskService.save(any<Task>()) }
    }

    @Test
    fun `felles metrikk teller utflytting korrigert og annullert uten task`() {
        val korrigert = hendelseTeller("utflytting", "korrigert", "registrert")
        val annullert = hendelseTeller("utflytting", "annullert", "registrert")
        val førKorrigert = korrigert.count()
        val førAnnullert = annullert.count()

        service.prosesserNyHendelse(hendelse(LeesahService.OPPLYSNINGSTYPE_UTFLYTTING, LeesahService.KORRIGERT))
        service.prosesserNyHendelse(hendelse(LeesahService.OPPLYSNINGSTYPE_UTFLYTTING, LeesahService.ANNULLERT))

        assertThat(korrigert.count()).isEqualTo(førKorrigert + 1)
        assertThat(annullert.count()).isEqualTo(førAnnullert + 1)
        verify(exactly = 0) { mockTaskService.save(any<Task>()) }
    }

    @Test
    fun `dødsfall uten dato telles fortsatt som ignorert men oppretter ikke task`() {
        val ignorertFør = service.dødsfallIgnorertCounter.count()
        val nyIgnorert = hendelseTeller("doedsfall", resultat = "ignorert")
        val førNyIgnorert = nyIgnorert.count()
        val teller = opprettetHendelseTeller("doedsfall")
        val før = teller.count()

        service.prosesserNyHendelse(hendelse(LeesahService.OPPLYSNINGSTYPE_DØDSFALL).copy(dødsdato = null))

        assertThat(service.dødsfallIgnorertCounter.count()).isEqualTo(ignorertFør + 1)
        assertThat(nyIgnorert.count()).isEqualTo(førNyIgnorert + 1)
        assertThat(teller.count()).isEqualTo(før)
        verify(exactly = 0) { mockTaskService.save(any<Task>()) }
    }

    @Test
    fun `teller ikke hendelse hvis andre tasklagring feiler`() {
        val teller = opprettetHendelseTeller("doedsfall")
        val før = teller.count()
        var lagringer = 0
        every { mockTaskService.save(any<Task>()) } answers {
            lagringer++
            if (lagringer == 2) throw RuntimeException("lagring feilet")
            Task("dummy", "payload")
        }

        assertThatThrownBy { service.prosesserNyHendelse(hendelse(LeesahService.OPPLYSNINGSTYPE_DØDSFALL)) }
            .isInstanceOf(RuntimeException::class.java)
        assertThat(teller.count()).isEqualTo(før)
    }

    @Test
    fun `annullert og korrigert sivilstand telles ikke som opprettet`() {
        val før = service.sivilstandOpprettetCounter.count()
        val opprettet = hendelseTeller("sivilstand", resultat = "registrert")
        val førOpprettet = opprettet.count()
        for (endringstype in listOf(LeesahService.ANNULLERT, LeesahService.KORRIGERT)) {
            service.prosesserNyHendelse(hendelse(LeesahService.OPPLYSNINGSTYPE_SIVILSTAND, endringstype))
        }
        assertThat(service.sivilstandOpprettetCounter.count()).isEqualTo(før)
        assertThat(opprettet.count()).isEqualTo(førOpprettet)
        assertThat(hendelseTeller("sivilstand", "annullert", "ignorert").count()).isEqualTo(1.0)
        assertThat(hendelseTeller("sivilstand", "korrigert", "ignorert").count()).isEqualTo(1.0)
        verify(exactly = 0) { mockTaskService.save(any<Task>()) }
    }

    @Test
    fun `Skal opprette VurderBarnetrygdLivshendelseTask og VurderKontantstøtteLivshendelseTask for dødsfallhendelse`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_DØDSFALL,
                dødsdato = LocalDate.now(),
            )

        service.prosesserNyHendelse(pdlHendelse)

        val taskList = mutableListOf<Task>()
        verify {
            mockTaskService.save(capture(taskList))
        }
        assertThat(taskList[0]).isNotNull
        assertThat(taskList[0].payload).contains("\"personIdent\":\"12345678901\",\"type\":\"DØDSFALL\"")
        assertThat(taskList[0].type).isEqualTo(VurderBarnetrygdLivshendelseTask.TASK_STEP_TYPE)

        assertThat(taskList[1]).isNotNull
        assertThat(taskList[1].payload).contains("\"personIdent\":\"12345678901\",\"type\":\"DØDSFALL\"")
        assertThat(taskList[1].type).isEqualTo(VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE)

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette VurderBarnetrygdLivshendelseTask og VurderKontantstøtteLivshendelseTask for utflyttingshendelse`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_UTFLYTTING,
                utflyttingsdato = LocalDate.now(),
            )

        service.prosesserNyHendelse(pdlHendelse)

        val taskList = mutableListOf<Task>()

        verify {
            mockTaskService.save(capture(taskList))
        }
        assertThat(taskList[0]).isNotNull
        assertThat(taskList[0].payload).contains("\"personIdent\":\"12345678901\",\"type\":\"UTFLYTTING\"")
        assertThat(taskList[0].type).isEqualTo(VurderBarnetrygdLivshendelseTask.TASK_STEP_TYPE)

        assertThat(taskList[1]).isNotNull
        assertThat(taskList[1].payload).contains("\"personIdent\":\"12345678901\",\"type\":\"UTFLYTTING\"")
        assertThat(taskList[1].type).isEqualTo(VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE)

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette VurderBarnetrygdLivshendelseTask for sivilstandhendelse GIFT`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_SIVILSTAND,
                sivilstand = "GIFT",
                sivilstandDato = LocalDate.of(2022, 2, 22),
            )

        service.prosesserNyHendelse(pdlHendelse)
        service.prosesserNyHendelse(pdlHendelse.copy(sivilstand = "UOPPGITT"))

        val taskSlot = slot<Task>()
        verify(exactly = 1) {
            mockTaskService.save(capture(taskSlot))
        }
        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload)
            .isEqualTo("{\"personIdent\":\"12345678901\",\"type\":\"SIVILSTAND\"}")
        assertThat(taskSlot.captured.type).isEqualTo(VurderBarnetrygdLivshendelseTask.TASK_STEP_TYPE)

        verify(exactly = 2) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette MottaFødselshendelseTask med fnr på payload`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_FØDSELSDATO,
                fødselsdato = LocalDate.now(),
                fødeland = "NOR",
            )

        service.prosesserNyHendelse(pdlHendelse)

        val taskSlot = slot<Task>()
        verify {
            mockTaskService.save(capture(taskSlot))
        }

        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.payload).isEqualTo("12345678901")
        assertThat(taskSlot.captured.type).isEqualTo(MottaFødselshendelseTask.TASK_STEP_TYPE)

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette MottaAnnullerFødselTask når endringstype er ANNULLERT`() {
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextUInt().toLong(),
                gjeldendeAktørId = "1234567890123",
                hendelseId = hendelseId,
                personIdenter = listOf("12345678901", "1234567890123"),
                endringstype = LeesahService.ANNULLERT,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_FØDSELSDATO,
                fødselsdato = LocalDate.now(),
                fødeland = "NOR",
                tidligereHendelseId = "unknown",
            )

        service.prosesserNyHendelse(pdlHendelse)

        val taskSlot = slot<Task>()
        verify {
            mockTaskService.save(capture(taskSlot))
        }

        assertThat(taskSlot.captured).isNotNull
        assertThat(taskSlot.captured.metadata["tidligereHendelseId"]).isEqualTo("unknown")
        assertThat(taskSlot.captured.type).isEqualTo(MottaAnnullerFødselTask.TASK_STEP_TYPE)

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal opprette FinnmarkstilleggTask for OPPLYSNINGSTYPE_BOSTEDSADRESSE`() {
        // Arrange
        val ident = "12345678910"
        val bostedskommune = "0301"
        val bostedskommuneFomDato = LocalDate.of(2025, 1, 1)
        val hendelseId = UUID.randomUUID().toString()

        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextLong(),
                gjeldendeAktørId = ident,
                hendelseId = hendelseId,
                personIdenter = listOf(ident),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_BOSTEDSADRESSE,
                bostedskommune = bostedskommune,
                bostedskommuneFomDato = bostedskommuneFomDato,
            )

        every { mockenv.activeProfiles } returns arrayOf("prod")

        // Act
        service.prosesserNyHendelse(pdlHendelse)

        // Assert
        val taskSlot = slot<Task>()
        verify(exactly = 1) { mockTaskService.save(capture(taskSlot)) }

        val task = taskSlot.captured
        assertThat(task.id).isEqualTo(0L)
        assertThat(task.metadata["callId"]).isEqualTo(hendelseId)
        assertThat(task.metadata["ident"]).isEqualTo(ident)
        assertThat(task.triggerTid).isCloseTo(LocalDateTime.now(), byLessThan(3, MINUTES))

        val payload = jsonMapper.readValue(taskSlot.captured.payload, VurderFinnmarkstillleggTaskDTO::class.java)
        assertThat(payload.ident).isEqualTo(ident)
        assertThat(payload.bostedskommune).isEqualTo(bostedskommune)
        assertThat(payload.bostedskommuneFomDato).isEqualTo(bostedskommuneFomDato)
    }

    @Test
    fun `Skal opprette SvalbardtilleggTask for OPPLYSNINGSTYPE_OPPHOLDSADRESSE`() {
        // Arrange
        val ident = "12345678910"
        val hendelseId = UUID.randomUUID().toString()

        val pdlHendelse =
            PdlHendelse(
                offset = Random.nextLong(),
                gjeldendeAktørId = ident,
                hendelseId = hendelseId,
                personIdenter = listOf(ident),
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_OPPHOLDSADRESSE,
            )

        every { mockenv.activeProfiles } returns arrayOf("prod")

        // Act
        service.prosesserNyHendelse(pdlHendelse)

        // Assert
        val taskSlot = slot<Task>()
        verify(exactly = 1) { mockTaskService.save(capture(taskSlot)) }

        val task = taskSlot.captured
        assertThat(task.id).isEqualTo(0L)
        assertThat(task.metadata["callId"]).isEqualTo(hendelseId)
        assertThat(task.metadata["ident"]).isEqualTo(ident)
        assertThat(task.triggerTid).isCloseTo(LocalDateTime.now(), byLessThan(3, MINUTES))
        assertThat(task.payload).isEqualTo(ident)
    }
}
