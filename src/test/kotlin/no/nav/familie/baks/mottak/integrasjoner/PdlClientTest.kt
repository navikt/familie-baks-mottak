package no.nav.familie.baks.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.familie.baks.mottak.AbstractWiremockTest
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import org.apache.commons.lang3.StringUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@ActiveProfiles("dev", "mock-oauth")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PdlClientTest : AbstractWiremockTest() {
    @Autowired
    lateinit var pdlClient: PdlClient

    @Test
    fun hentIdenter() {
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentIdenter.graphql", testIdent),
            mockResponse = readfile("mockIdentInformasjonResponse.json"),
        )

        val identer = pdlClient.hentIdenter(testIdent, Tema.BAR)
        assertThat(identer).extracting("ident").contains(testIdent)
        assertThat(identer).extracting("gruppe").containsAll(listOf("FOLKEREGISTERIDENT", "AKTORID"))
        assertThat(identer).extracting("historisk").containsAll(listOf(true, false))
    }

    @Test
    fun hentPersonMedRelasjoner() {
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", testIdent),
            mockResponse = readfile("mockPersonResponse.json"),
        )

        val personInfo = pdlClient.hentPersonMedRelasjoner(testIdent, Tema.BAR)
        assertThat(personInfo.adressebeskyttelseGradering).isEqualTo(listOf(Adressebeskyttelsesgradering.UGRADERT))
        assertThat(personInfo.forelderBarnRelasjoner.size).isEqualTo(2)
        assertThat(personInfo.bostedsadresse?.vegadresse).isNotNull
    }

    @Test
    fun hentPersonDødsfallFamilierelasjon() {
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-relasjon-dødsfall.graphql", testIdent),
            mockResponse = readfile("mock-hentperson-relasjon-dødsfall.json"),
        )

        val pdlPersonData = pdlClient.hentPerson(testIdent, "hentperson-relasjon-dødsfall", Tema.BAR)
        assertThat(pdlPersonData.forelderBarnRelasjon.size).isEqualTo(1)
        assertThat(pdlPersonData.forelderBarnRelasjon.first().minRolleForPerson).isEqualTo(FORELDERBARNRELASJONROLLE.MOR)
        assertThat(pdlPersonData.forelderBarnRelasjon.first().relatertPersonsRolle).isEqualTo(FORELDERBARNRELASJONROLLE.BARN)
        assertThat(pdlPersonData.dødsfall.first().dødsdato).isEqualTo(LocalDate.of(2021, 1, 14))
        assertThat(pdlPersonData.fødsel.first().fødselsdato).isEqualTo(LocalDate.of(1998, 5, 9))
    }

    @Test
    fun hentPersonFødested() {
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-fødested.graphql", testIdent),
            mockResponse = readfile("mock-hentperson-fødested.json"),
        )

        val pdlPersonData = pdlClient.hentPerson(testIdent, "hentperson-fødested", Tema.BAR)

        assertThat(pdlPersonData.fødested.first().fødeland).isEqualTo("NOR")
    }

    @Test
    fun `skal hente person med adressebeskyttelse fra pdl`() {
        // Arrange
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-med-adressebeskyttelse.graphql", testIdent),
            mockResponse = readfile("mock-hentperson-adressebeskyttelse.json"),
        )

        // Act
        val pdlPersonData =
            pdlClient.hentPerson(
                personIdent = testIdent,
                graphqlfil = "hentperson-med-adressebeskyttelse",
                tema = Tema.BAR,
            )

        // Assert
        assertThat(pdlPersonData.adressebeskyttelse).hasSize(1)
        assertThat(pdlPersonData.adressebeskyttelse.first().gradering).isEqualTo(Adressebeskyttelsesgradering.UGRADERT)
    }

    @Test
    fun hentPersonFeilerMedInternalServerOgKasterIntegrasjonsException() {
        stubFor(
            post(urlEqualTo("/api/graphql"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(500),
                ),
        )

        assertThatThrownBy {
            pdlClient.hentPerson(testIdent, "hentperson-relasjon-dødsfall", Tema.BAR)
        }.isInstanceOf(IntegrasjonException::class.java)
            .hasMessageContaining("Feil ved oppslag på hentPerson mot PDL. Gav feil: 500 Server Error")
    }

    @Test
    fun hentPersonGraphqlReturnererFeilmeldingOgDetKastesEnIntegrajonsException() {
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-relasjon-dødsfall.graphql", testIdent),
            mockResponse = readfile("mock-error-response.json"),
        )

        assertThatThrownBy {
            pdlClient.hentPerson(testIdent, "hentperson-relasjon-dødsfall", Tema.BAR)
        }.isInstanceOf(IntegrasjonException::class.java)
            .hasMessageContaining("Feil ved oppslag på hentPerson mot PDL: Fant ikke person")
    }

    @Test
    fun `skal kunne hente personIdent basert på aktørID`() {
        // Arrange
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentIdenter.graphql", testIdent),
            mockResponse = readfile("mockIdentInformasjonResponse.json"),
        )

        // Act
        val ident = pdlClient.hentPersonident(testIdent, Tema.BAR)

        // Assert
        assertThat(ident).contains(testIdent)
    }

    @Test
    fun `skal kaste feil dersom vi ikke finner en aktiv personIdent basert på aktørID`() {
        // Arrange
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentIdenter.graphql", testIdent),
            mockResponse = readfile("mock-ident-informasjon-kun-historiske-identer.json"),
        )

        // Act & assert
        val exception =
            assertThrows<PdlNotFoundException> {
                val ident = pdlClient.hentPersonident(testIdent, Tema.BAR)
            }
        assertThat(exception.message).isEqualTo("Fant ikke aktive identer på person")
    }

    companion object {
        val testIdent = "12345678901"

        fun mockResponseForPdlQuery(
            pdlRequestBody: String,
            mockResponse: String,
        ) {
            stubFor(
                post(urlEqualTo("/api/graphql"))
                    .withRequestBody(equalToJson(pdlRequestBody))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(mockResponse),
                    ),
            )
        }

        fun gyldigRequest(
            queryFilnavn: String,
            ident: String,
        ): String = "{\"variables\":{\"ident\":\"$ident\"},\"query\":\"${readfile(queryFilnavn).graphqlCompatible()}\"}"

        fun readfile(filnavn: String): String = this::class.java.getResource("/pdl/$filnavn").readText()

        private fun String.graphqlCompatible(): String = StringUtils.normalizeSpace(this.replace("\n", ""))
    }
}
