package no.nav.familie.baks.mottak.task

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.mockk.clearAllMocks
import no.nav.familie.baks.mottak.AbstractWiremockTest
import no.nav.familie.baks.mottak.domene.NyBehandling
import no.nav.familie.baks.mottak.integrasjoner.Adressebeskyttelse
import no.nav.familie.baks.mottak.integrasjoner.Adressebeskyttelsesgradering
import no.nav.familie.baks.mottak.integrasjoner.Fødested
import no.nav.familie.baks.mottak.integrasjoner.IntegrasjonException
import no.nav.familie.baks.mottak.integrasjoner.PdlError
import no.nav.familie.baks.mottak.integrasjoner.PdlForeldreBarnRelasjon
import no.nav.familie.baks.mottak.integrasjoner.PdlHentPersonResponse
import no.nav.familie.baks.mottak.integrasjoner.PdlPerson
import no.nav.familie.baks.mottak.integrasjoner.PdlPersonData
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.kontrakter.felles.personopplysning.Matrikkeladresse
import no.nav.familie.log.mdc.MDCConstants
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.apache.commons.lang3.StringUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Pageable
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@ActiveProfiles("postgres", "mock-oauth", "mock-sts", "testcontainers")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MottaFødselshendelseTaskTest : AbstractWiremockTest() {
    @Autowired
    lateinit var taskService: TaskService

    @Autowired
    lateinit var mottaFødselshendelseTask: MottaFødselshendelseTask

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
            mockResponse =
                PdlHentPersonResponse(
                    data = PdlPerson(lagTestPdlPerson()),
                    errors = emptyList(),
                    extensions = null,
                ),
        )
        mockFødestedNorge(fnrBarn)

        mottaFødselshendelseTask.doTask(Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = fnrBarn))

        val taskerMedCallId =
            taskService
                .finnTasksMedStatus(listOf(Status.UBEHANDLET), null, Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).hasSize(1).extracting("type").containsOnly(SendTilBaSakTask.TASK_STEP_TYPE)
        assertThat(jsonMapper.readValue(taskerMedCallId.first().payload, NyBehandling::class.java))
            .hasFieldOrPropertyWithValue("morsIdent", "20107678901")
            .hasFieldOrPropertyWithValue("barnasIdenter", arrayOf(fnrBarn))
    }

    private fun mockFødestedNorge(fnrBarn: String) {
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-fødested.graphql", fnrBarn),
            mockResponse =
                PdlHentPersonResponse(
                    data = PdlPerson(PdlPersonData(fødested = listOf(Fødested(fødeland = "NOR")))),
                    errors = emptyList(),
                    extensions = null,
                ),
        )
    }

    @Test
    fun `Skal opprette SendTilSak task med NyBehandling som payload hvis barnet har gyldig fnr, har mor, barn har addresseGradering og bostedsadresse null`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "12345"

        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", fnrBarn),
            mockResponse =
                PdlHentPersonResponse(
                    data =
                        PdlPerson(
                            lagTestPdlPerson().copy(
                                bostedsadresse = emptyList(),
                                adressebeskyttelse =
                                    listOf(
                                        Adressebeskyttelse(
                                            Adressebeskyttelsesgradering.STRENGT_FORTROLIG,
                                        ),
                                    ),
                            ),
                        ),
                    errors = emptyList(),
                    extensions = null,
                ),
        )
        mockFødestedNorge(fnrBarn)

        mottaFødselshendelseTask.doTask(Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, fnrBarn))

        val taskerMedCallId =
            taskService
                .finnTasksMedStatus(listOf(Status.UBEHANDLET), null, Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).hasSize(1).extracting("type").containsOnly(SendTilBaSakTask.TASK_STEP_TYPE)
        assertThat(jsonMapper.readValue(taskerMedCallId.first().payload, NyBehandling::class.java))
            .hasFieldOrPropertyWithValue("morsIdent", "20107678901")
            .hasFieldOrPropertyWithValue("barnasIdenter", arrayOf(fnrBarn))
    }

    @Test
    fun `Skal filtrere bort fdat, bost og dnr på barn`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val barnHarDnrCountFørTest = mottaFødselshendelseTask.barnHarDnrCounter.count()
        val task = Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = "02062000000")

        mottaFødselshendelseTask.doTask(task)

        val taskerMedCallId =
            taskService
                .finnTasksMedStatus(listOf(Status.UBEHANDLET), null, Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).isEmpty()
        assertThat(mottaFødselshendelseTask.barnHarDnrCounter.count()).isGreaterThan(barnHarDnrCountFørTest)
    }

    @Test
    fun `Skal filtrere bort fdat, bost og dnr på mor`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "54321"
        val forsørgerHarDnrCountFørTest = mottaFødselshendelseTask.forsørgerHarDnrCounter.count()

        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", fnrBarn),
            mockResponse =
                PdlHentPersonResponse(
                    data =
                        PdlPerson(
                            lagTestPdlPerson().copy(
                                forelderBarnRelasjon =
                                    listOf(
                                        PdlForeldreBarnRelasjon(
                                            "40107678901",
                                            FORELDERBARNRELASJONROLLE.MOR,
                                        ),
                                    ),
                            ),
                        ),
                    errors = emptyList(),
                    extensions = null,
                ),
        )
        mockFødestedNorge(fnrBarn)

        val task = Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = fnrBarn)

        mottaFødselshendelseTask.doTask(task)

        val taskerMedCallId =
            taskService
                .finnTasksMedStatus(listOf(Status.UBEHANDLET), null, Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).isEmpty()
        assertThat(mottaFødselshendelseTask.forsørgerHarDnrCounter.count()).isGreaterThan(forsørgerHarDnrCountFørTest)
    }

    @Test
    fun `Skal filtrere bort hendelse hvis person ikke har registrert mor`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "12123"

        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", fnrBarn),
            mockResponse =
                PdlHentPersonResponse(
                    data =
                        PdlPerson(
                            lagTestPdlPerson().copy(
                                forelderBarnRelasjon =
                                    listOf(
                                        PdlForeldreBarnRelasjon(
                                            "20107678901",
                                            FORELDERBARNRELASJONROLLE.FAR,
                                        ),
                                    ),
                            ),
                        ),
                    errors = emptyList(),
                    extensions = null,
                ),
        )
        mockFødestedNorge(fnrBarn)

        val task = Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = fnrBarn)

        mottaFødselshendelseTask.doTask(task)

        val taskerMedCallId =
            taskService
                .finnTasksMedStatus(listOf(Status.UBEHANDLET), null, Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).isEmpty()
    }

    @Test
    fun `Skal filtrere bort hendelse hvis barn ikke har registrert bostedsadresse`() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())
        val fnrBarn = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")) + "13331"
        val barnetManglerBostedsadresseCountFørTest = mottaFødselshendelseTask.barnetManglerBostedsadresse.count()

        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", fnrBarn),
            mockResponse =
                PdlHentPersonResponse(
                    data = PdlPerson(lagTestPdlPerson().copy(bostedsadresse = emptyList())),
                    errors = emptyList(),
                    extensions = null,
                ),
        )
        mockFødestedNorge(fnrBarn)

        val task = Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = fnrBarn)

        mottaFødselshendelseTask.doTask(task)

        val taskerMedCallId =
            taskService
                .finnTasksMedStatus(listOf(Status.UBEHANDLET), null, Pageable.unpaged())
                .filter { it.callId == MDC.get(MDCConstants.MDC_CALL_ID) }

        assertThat(taskerMedCallId).isEmpty()
        assertThat(mottaFødselshendelseTask.barnetManglerBostedsadresse.count()).isGreaterThan(barnetManglerBostedsadresseCountFørTest)
    }

    @Test
    fun `Feil mot PDL kaster IntegrasjonException `() {
        MDC.put(MDCConstants.MDC_CALL_ID, UUID.randomUUID().toString())

        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", "02091901252"),
            mockResponse =
                PdlHentPersonResponse(
                    data =
                        PdlPerson(
                            lagTestPdlPerson().copy(
                                forelderBarnRelasjon =
                                    listOf(
                                        PdlForeldreBarnRelasjon(
                                            "40107678901",
                                            FORELDERBARNRELASJONROLLE.MOR,
                                        ),
                                    ),
                            ),
                        ),
                    errors = listOf(PdlError("Feilmelding", null)),
                    extensions = null,
                ),
        )
        mockFødestedNorge("02091901252")

        val task = Task(type = MottaFødselshendelseTask.TASK_STEP_TYPE, payload = "02091901252")

        assertThatThrownBy { mottaFødselshendelseTask.doTask(task) }
            .isInstanceOf(IntegrasjonException::class.java)
            .hasMessage("Feil ved oppslag på person: Feilmelding")
    }

    private fun lagTestPdlPerson(): PdlPersonData =
        PdlPersonData(
            forelderBarnRelasjon =
                listOf(
                    PdlForeldreBarnRelasjon(
                        "20107678901",
                        FORELDERBARNRELASJONROLLE.MOR,
                    ),
                ),
            bostedsadresse = listOf(Bostedsadresse(matrikkeladresse = Matrikkeladresse(1, "1", null, "0576", "3000"))),
        )

    companion object {
        private fun mockResponseForPdlQuery(
            pdlRequestBody: String,
            mockResponse: PdlHentPersonResponse,
        ) {
            stubFor(
                post(urlEqualTo("/api/graphql"))
                    .withRequestBody(WireMock.equalToJson(pdlRequestBody))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(jsonMapper.writeValueAsString(mockResponse)),
                    ),
            )
        }

        private fun gyldigRequest(
            queryFilnavn: String,
            ident: String,
        ): String = "{\"variables\":{\"ident\":\"$ident\"},\"query\":\"${readfile(queryFilnavn).graphqlCompatible()}\"}"

        private fun readfile(filnavn: String): String = this::class.java.getResource("/pdl/$filnavn").readText()

        private fun String.graphqlCompatible(): String = StringUtils.normalizeSpace(this.replace("\n", ""))
    }
}
