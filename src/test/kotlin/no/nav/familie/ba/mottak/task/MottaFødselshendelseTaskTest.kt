package no.nav.familie.ba.mottak.task

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.mockk.clearAllMocks
import no.nav.familie.ba.mottak.DevLauncher
import no.nav.familie.ba.mottak.domene.NyBehandling
import no.nav.familie.ba.mottak.domene.personopplysning.Person
import no.nav.familie.ba.mottak.integrasjoner.Adressebeskyttelse
import no.nav.familie.ba.mottak.integrasjoner.Adressebeskyttelsesgradering
import no.nav.familie.ba.mottak.integrasjoner.IntegrasjonException
import no.nav.familie.ba.mottak.integrasjoner.PdlError
import no.nav.familie.ba.mottak.integrasjoner.PdlForeldreBarnRelasjon
import no.nav.familie.ba.mottak.integrasjoner.PdlHentPersonResponse
import no.nav.familie.ba.mottak.integrasjoner.PdlPerson
import no.nav.familie.ba.mottak.integrasjoner.PdlPersonData
import no.nav.familie.kontrakter.felles.Ressurs.Companion.success
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.kontrakter.felles.personopplysning.ForelderBarnRelasjon
import no.nav.familie.kontrakter.felles.personopplysning.Matrikkeladresse
import no.nav.familie.log.mdc.MDCConstants
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import no.nav.familie.prosessering.error.RekjørSenereException
import org.apache.commons.lang3.StringUtils
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertFailsWith


@SpringBootTest(classes = [DevLauncher::class],
                properties = ["PDL_URL=http://localhost:28085/api", "FAMILIE_INTEGRASJONER_API_URL=http://localhost:28085/api"])
@ActiveProfiles("dev", "mock-oauth", "mock-sts")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MottaFødselshendelseTaskTest{

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

        mockResponseForPdlQuery(
                pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", fnrBarn),
                mockResponse = PdlHentPersonResponse(
                        data = PdlPerson(lagTestPdlPerson()),
                        errors = emptyList()
                )
        )
        //TODO fjernes når barnetrygd er ute av infotrygd
        stubFor(post(urlEqualTo("/api/personopplysning/v2/info"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(objectMapper.writeValueAsString(
                                                    success(lagTestPerson().copy(bostedsadresse = null))))))


        taskService.doTask(Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = fnrBarn))

        val taskerMedCallId = taskRepository.findByStatusIn(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).hasSize(1).extracting("type").containsOnly(SendTilSakTask.TASK_STEP_TYPE)
        assertThat(objectMapper.readValue(taskerMedCallId.first().payload, NyBehandling::class.java))
                .hasFieldOrPropertyWithValue("morsIdent", "20107678901")
                .hasFieldOrPropertyWithValue("barnasIdenter", arrayOf(fnrBarn))
    }

    @Test
    fun `Skal opprette SendTilSak task med NyBehandling som payload hvis barnet har gyldig fnr, har mor, barn har addresseGradering og bostedsadresse null`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "12345"

        mockResponseForPdlQuery(
                pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", fnrBarn),
                mockResponse = PdlHentPersonResponse(
                        data = PdlPerson(lagTestPdlPerson().copy(bostedsadresse = emptyList(),
                                                                 adressebeskyttelse = listOf(Adressebeskyttelse(
                                                                         Adressebeskyttelsesgradering.STRENGT_FORTROLIG)))),
                        errors = emptyList()
                )
        )
        //TODO fjernes når barnetrygd er ute av infotrygd
        stubFor(post(urlEqualTo("/api/personopplysning/v2/info"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(objectMapper.writeValueAsString(
                                                    success(lagTestPerson().copy(bostedsadresse = null))))))


        taskService.doTask(Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, fnrBarn))

        val taskerMedCallId = taskRepository.findByStatusIn(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).hasSize(1).extracting("type").containsOnly(SendTilSakTask.TASK_STEP_TYPE)
        assertThat(objectMapper.readValue(taskerMedCallId.first().payload, NyBehandling::class.java))
                .hasFieldOrPropertyWithValue("morsIdent", "20107678901")
                .hasFieldOrPropertyWithValue("barnasIdenter", arrayOf(fnrBarn))
    }


    @Test
    fun `Skal ikke opprette SendTilSak task og kaste RekjørSenereException hvis personen ikke finnes i TPS`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "12345"

        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", fnrBarn),
            mockResponse = PdlHentPersonResponse(
                data = PdlPerson(lagTestPdlPerson().copy(forelderBarnRelasjon = listOf(
                    PdlForeldreBarnRelasjon(
                        "40107678901",
                        FORELDERBARNRELASJONROLLE.MOR)))),
                errors = emptyList()
            )
        )

        //skal fjernes når barnetrygd er ute av infotrygd
        stubFor(post(urlEqualTo("/api/personopplysning/v2/info"))
                        .willReturn(aResponse().withStatus(404)))

        val exception = assertFailsWith<RekjørSenereException>("Kall mot integrasjon feilet ved uthenting av personinfo. 404 NOT_FOUND") {
            taskService.doTask(Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = fnrBarn))
        }

        assertThat(exception.årsak).isEqualTo("MottaFødselshendelseTask feilet")
        assertThat(exception.triggerTid).isAfter(LocalDateTime.now())
    }

    @Test
    fun `Skal filtrere bort fdat, bost og dnr på barn`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val barnHarDnrCountFørTest = taskService.barnHarDnrCounter.count()
        val task = Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = "02062000000")

        taskService.doTask(task)

        val taskerMedCallId = taskRepository.findByStatusIn(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).isEmpty()
        assertThat(taskService.barnHarDnrCounter.count()).isGreaterThan(barnHarDnrCountFørTest)
    }

    @Test
    fun `Skal filtrere bort fdat, bost og dnr på mor`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "54321"
        val forsørgerHarDnrCountFørTest = taskService.forsørgerHarDnrCounter.count()


        mockResponseForPdlQuery(
                pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", fnrBarn),
                mockResponse = PdlHentPersonResponse(
                        data = PdlPerson(lagTestPdlPerson().copy(forelderBarnRelasjon = listOf(
                                PdlForeldreBarnRelasjon(
                                        "40107678901",
                                        FORELDERBARNRELASJONROLLE.MOR)))),
                        errors = emptyList()
                )
        )

        val task = Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = fnrBarn)

        taskService.doTask(task)

        val taskerMedCallId = taskRepository.findByStatusIn(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).isEmpty()
        assertThat(taskService.forsørgerHarDnrCounter.count()).isGreaterThan(forsørgerHarDnrCountFørTest)
    }

    @Test
    fun `Skal filtrere bort hendelse hvis person ikke har registrert mor`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "12123"


        mockResponseForPdlQuery(
                pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", fnrBarn),
                mockResponse = PdlHentPersonResponse(
                        data = PdlPerson(lagTestPdlPerson().copy(forelderBarnRelasjon =
                                                                 listOf(PdlForeldreBarnRelasjon(
                                                                         "20107678901",
                                                                         FORELDERBARNRELASJONROLLE.FAR)))),
                        errors = emptyList()
                )
        )
        //TODO fjernes når barnetrygd er ute av infotrygd
        stubFor(post(urlEqualTo("/api/personopplysning/v2/info"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(objectMapper.writeValueAsString(
                                                    success(lagTestPerson().copy(forelderBarnRelasjoner =
                                                                                 setOf(ForelderBarnRelasjon(
                                                                                         "20107678901",
                                                                                         FORELDERBARNRELASJONROLLE.FAR))))))))

        val task = Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = fnrBarn)

        taskService.doTask(task)

        val taskerMedCallId = taskRepository.findByStatusIn(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).isEmpty()
    }

    @Test
    fun `Skal filtrere bort hendelse hvis barn ikke har registrert bostedsadresse`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "13331"
        val barnetManglerBostedsadresseCountFørTest = taskService.barnetManglerBostedsadresse.count()

        mockResponseForPdlQuery(
                pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", fnrBarn),
                mockResponse = PdlHentPersonResponse(
                        data = PdlPerson(lagTestPdlPerson().copy(bostedsadresse = emptyList())),
                        errors = emptyList()
                )
        )
        //TODO fjernes når barnetrygd er ute av infotrygd
        stubFor(post(urlEqualTo("/api/personopplysning/v2/info"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(objectMapper.writeValueAsString(
                                                    success(lagTestPerson().copy(bostedsadresse = null))))))

        val task = Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = fnrBarn)

        taskService.doTask(task)

        val taskerMedCallId = taskRepository.findByStatusIn(listOf(Status.UBEHANDLET), Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).isEmpty()
        assertThat(taskService.barnetManglerBostedsadresse.count()).isGreaterThan(barnetManglerBostedsadresseCountFørTest)
    }

    @Test
    fun `Skal kaste Feil og sette rekjøringsintervall frem i tid for mottaFødselshendelseTask`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())

        mockResponseForPdlQuery(
                pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", "02091901252"),
                mockResponse = PdlHentPersonResponse(
                        data = PdlPerson(lagTestPdlPerson().copy(forelderBarnRelasjon = listOf(
                                PdlForeldreBarnRelasjon(
                                        "40107678901",
                                        FORELDERBARNRELASJONROLLE.MOR)))),
                        errors = listOf(PdlError("Feilmelding"))
                )
        )

        val task = Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = "02091901252")

        assertThatThrownBy { taskService.doTask(task) }.isInstanceOf(IntegrasjonException::class.java)
                .hasMessage("Feil ved oppslag på person: Feilmelding")
    }

    private fun lagTestPerson(): Person {
        return Person("Test Person",
                      setOf(ForelderBarnRelasjon(
                              "20107678901",
                              FORELDERBARNRELASJONROLLE.MOR)),
                      Bostedsadresse(matrikkeladresse = Matrikkeladresse(1, "1", null, "0576", "3000"))
        )
    }

    private fun lagTestPdlPerson(): PdlPersonData {
        return PdlPersonData(
                forelderBarnRelasjon = listOf(PdlForeldreBarnRelasjon(
                        "20107678901",
                        FORELDERBARNRELASJONROLLE.MOR)),
                bostedsadresse = listOf(Bostedsadresse(matrikkeladresse = Matrikkeladresse(1, "1", null, "0576", "3000")))
        )
    }

    companion object {

        private fun mockResponseForPdlQuery(pdlRequestBody: String, mockResponse: PdlHentPersonResponse) {
            stubFor(
                    post(urlEqualTo("/api/graphql"))
                            .withRequestBody(WireMock.equalToJson(pdlRequestBody))
                            .willReturn(
                                    aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(objectMapper.writeValueAsString(mockResponse))
                            )
            )
        }

        private fun gyldigRequest(queryFilnavn: String, ident: String): String {
            return "{\"variables\":{\"ident\":\"$ident\"},\"query\":\"${readfile(queryFilnavn).graphqlCompatible()}\"}"
        }

        private fun readfile(filnavn: String): String {
            return this::class.java.getResource("/pdl/$filnavn").readText()
        }

        private fun String.graphqlCompatible(): String {
            return StringUtils.normalizeSpace(this.replace("\n", ""))
        }

    }
}