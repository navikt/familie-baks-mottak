package no.nav.familie.ba.mottak.task

import com.github.tomakehurst.wiremock.client.WireMock.*
import io.mockk.clearAllMocks
import no.nav.familie.ba.mottak.DevLauncher
import no.nav.familie.ba.mottak.domene.NyBehandling
import no.nav.familie.ba.mottak.domene.personopplysning.Familierelasjon
import no.nav.familie.ba.mottak.domene.personopplysning.Person
import no.nav.familie.ba.mottak.domene.personopplysning.PersonIdent
import no.nav.familie.kontrakter.felles.Ressurs.Companion.success
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.kontrakter.felles.personopplysning.Matrikkeladresse
import no.nav.familie.log.mdc.MDCConstants
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.data.domain.Pageable
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.assertFailsWith

@SpringBootTest(classes = [DevLauncher::class], properties = ["FAMILIE_INTEGRASJONER_API_URL=http://localhost:28085/api"])
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MottaFødselshendelseTaskTest {

    @Autowired
    lateinit var taskRepository: TaskRepository

    @Autowired
    lateinit var taskService: MottaFødselshendelseTask

    @BeforeEach
    internal fun setUp() {
        clearAllMocks()
        MDC.clear()
    }

    @Test
    fun `Skal opprette SendTilSak task med NyBehandling som payload hvis barnet har gyldig fnr, har mor, ugradert og barn har bostedsadresse`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "12345"

        stubFor(get(urlEqualTo("/api/personopplysning/v1/info/BAR"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(objectMapper.writeValueAsString(
                                                    success(lagTestPerson())))))
        //TODO fjernes når barnetrygd er ute av infotrygd
        stubFor(post(urlEqualTo("/api/personopplysning/v2/info"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(
                                success(lagTestPerson().copy(bostedsadresse = null))))))


        taskService.doTask(Task.nyTask(MottaFødselshendelseTask.TASK_STEP_TYPE, fnrBarn))

        val taskerMedCallId = taskRepository.finnTasksMedStatus(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).hasSize(1).extracting("taskStepType").containsOnly(SendTilSakTask.TASK_STEP_TYPE)
        assertThat(objectMapper.readValue(taskerMedCallId.first().payload, NyBehandling::class.java))
                .hasFieldOrPropertyWithValue("morsIdent", "20107678901")
                .hasFieldOrPropertyWithValue("barnasIdenter", arrayOf(fnrBarn))
    }

    @Test
    fun `Skal opprette SendTilSak task med NyBehandling som payload hvis barnet har gyldig fnr, har mor, barn har addresseGradering og bostedsadresse null`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "12345"

        stubFor(get(urlEqualTo("/api/personopplysning/v1/info/BAR"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(objectMapper.writeValueAsString(
                                                    success(lagTestPerson().copy(bostedsadresse = null,
                                                                                 adressebeskyttelseGradering = "STRENGT_FORTROLIG"))))))
        //TODO fjernes når barnetrygd er ute av infotrygd
        stubFor(post(urlEqualTo("/api/personopplysning/v2/info"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(
                                success(lagTestPerson().copy(bostedsadresse = null))))))


        taskService.doTask(Task.nyTask(MottaFødselshendelseTask.TASK_STEP_TYPE, fnrBarn))

        val taskerMedCallId = taskRepository.finnTasksMedStatus(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).hasSize(1).extracting("taskStepType").containsOnly(SendTilSakTask.TASK_STEP_TYPE)
        assertThat(objectMapper.readValue(taskerMedCallId.first().payload, NyBehandling::class.java))
                .hasFieldOrPropertyWithValue("morsIdent", "20107678901")
                .hasFieldOrPropertyWithValue("barnasIdenter", arrayOf(fnrBarn))
    }


    @Test
    fun `Skal ikke opprette SendTilSak task hvis personen ikke finnes i TPS`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "12345"

        stubFor(get(urlEqualTo("/api/personopplysning/v1/info/BAR"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(
                                success(lagTestPerson())))))

        //TODO fjernes når barnetrygd er ute av infotrygd
        stubFor(post(urlEqualTo("/api/personopplysning/v2/info"))
                .willReturn(aResponse().withStatus(404)))

        assertFailsWith<RuntimeException>("Kall mot integrasjon feilet ved uthenting av personinfo. 404 NOT_FOUND") {
            taskService.doTask(Task.nyTask(MottaFødselshendelseTask.TASK_STEP_TYPE, fnrBarn))
        }

        val taskerMedCallId = taskRepository.finnTasksMedStatus(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).hasSize(1).extracting("taskStepType").containsOnly(MottaFødselshendelseTask.TASK_STEP_TYPE)
    }

    @Test
    fun `Skal filtrere bort dnr på barn`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val barnHarDnrCountFørTest = taskService.barnHarDnrCounter.count()
        val task = Task.nyTask(MottaFødselshendelseTask.TASK_STEP_TYPE, "42062000000")

        taskService.doTask(task)

        val taskerMedCallId = taskRepository.finnTasksMedStatus(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).isEmpty()
        assertThat(taskService.barnHarDnrCounter.count()).isGreaterThan(barnHarDnrCountFørTest)
    }

    @Test
    fun `Skal filtrere bort dnr på mor`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "54321"
        val forsørgerHarDnrCountFørTest = taskService.forsørgerHarDnrCounter.count()

        stubFor(get(urlEqualTo("/api/personopplysning/v1/info/BAR"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    objectMapper.writeValueAsString(
                                                            success(lagTestPerson().copy(familierelasjoner = setOf(
                                                                    Familierelasjon(
                                                                            PersonIdent(
                                                                                    "40107678901"),
                                                                            "MOR"))))))))

        val task = Task.nyTask(MottaFødselshendelseTask.TASK_STEP_TYPE, fnrBarn)

        taskService.doTask(task)

        val taskerMedCallId = taskRepository.finnTasksMedStatus(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).isEmpty()
        assertThat(taskService.forsørgerHarDnrCounter.count()).isGreaterThan(forsørgerHarDnrCountFørTest)
    }

    @Test
    fun `Skal filtrere bort hendelse hvis person ikke har registrert mor`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "12123"

        stubFor(get(urlEqualTo("/api/personopplysning/v1/info/BAR"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    objectMapper.writeValueAsString(
                                                            success(lagTestPerson().copy(familierelasjoner =
                                                                                         setOf(Familierelasjon(
                                                                                                 PersonIdent(
                                                                                                         "20107678901"),
                                                                                                 "FAR"))
                                                            ))))))

        val task = Task.nyTask(MottaFødselshendelseTask.TASK_STEP_TYPE, fnrBarn)

        taskService.doTask(task)

        val taskerMedCallId = taskRepository.finnTasksMedStatus(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).isEmpty()
    }

    @Test
    fun `Skal filtrere bort hendelse hvis barn ikke har registrert bostedsadresse`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "13331"
        val barnetManglerBostedsadresseCountFørTest = taskService.barnetManglerBostedsadresse.count()

        stubFor(get(urlEqualTo("/api/personopplysning/v1/info/BAR"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(objectMapper.writeValueAsString(
                                                    success(lagTestPerson().copy(bostedsadresse = null))))))

        //TODO fjernes når barnetrygd er ute av infotrygd
        stubFor(post(urlEqualTo("/api/personopplysning/v2/info"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(
                                success(lagTestPerson().copy(bostedsadresse = null))))))

        val task = Task.nyTask(MottaFødselshendelseTask.TASK_STEP_TYPE, fnrBarn)

        taskService.doTask(task)

        val taskerMedCallId = taskRepository.finnTasksMedStatus(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).isEmpty()
        assertThat(taskService.barnetManglerBostedsadresse.count()).isGreaterThan(barnetManglerBostedsadresseCountFørTest)
    }

    @Test
    fun `Skal kaste Feil og sette rekjøringsintervall frem i tid for mottaFødselshendelseTask`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())

        stubFor(get(urlEqualTo("/api/personopplysning/v1/info/BAR"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withStatus(500)))

        val task = Task.nyTask(MottaFødselshendelseTask.TASK_STEP_TYPE, "02091901252")

        assertThatThrownBy { taskService.doTask(task) }.isInstanceOf(RuntimeException::class.java)
                .hasMessage("Kall mot integrasjon feilet ved uthenting av personinfo. 500 INTERNAL_SERVER_ERROR ")

        val taskerMedCallId = taskRepository.finnTasksMedStatus(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).hasSize(1)
        assertThat(taskerMedCallId.first().taskStepType).isEqualTo(MottaFødselshendelseTask.TASK_STEP_TYPE)
        assertThat(taskerMedCallId.first().triggerTid).isAfter(taskerMedCallId.first().opprettetTidspunkt)
    }

    private fun lagTestPerson(): Person {
        return Person("Test Person",
                      setOf(Familierelasjon(
                              PersonIdent(
                                      "20107678901"),
                              "MOR")),
                      Bostedsadresse(matrikkeladresse = Matrikkeladresse(1, "1", null, "0576", "3000"))
        )
    }
}