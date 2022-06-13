package no.nav.familie.ba.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.familie.ba.mottak.DevLauncher
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import org.apache.commons.lang3.StringUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@SpringBootTest(classes = [DevLauncher::class], properties = ["PDL_URL=http://localhost:28085/api"])
@ActiveProfiles("dev", "mock-oauth", "mock-sts")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PdlClientTest {

    @Autowired
    lateinit var pdlClient: PdlClient

    @Test
    fun hentIdenter() {
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentIdenter.graphql", testIdent),
            mockResponse = readfile("mockIdentInformasjonResponse.json")
        )

        val identer = pdlClient.hentIdenter(testIdent)
        assertThat(identer).extracting("ident").contains(testIdent)
        assertThat(identer).extracting("gruppe").containsAll(listOf("FOLKEREGISTERIDENT", "AKTORID"))
        assertThat(identer).extracting("historisk").containsAll(listOf(true, false))
    }

    @Test
    fun hentPersonMedRelasjoner() {
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-med-relasjoner.graphql", testIdent),
            mockResponse = readfile("mockPersonResponse.json")
        )

        val personInfo = pdlClient.hentPersonMedRelasjoner(testIdent)
        assertThat(personInfo.adressebeskyttelseGradering).isEqualTo(Adressebeskyttelsesgradering.UGRADERT.name)
        assertThat(personInfo.forelderBarnRelasjoner.size).isEqualTo(2)
        assertThat(personInfo.bostedsadresse?.vegadresse).isNotNull
    }

    @Test
    fun hentPersonDødsfallFamilierelasjon() {
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-relasjon-dødsfall.graphql", testIdent),
            mockResponse = readfile("mock-hentperson-relasjon-dødsfall.json")
        )

        val pdlPersonData = pdlClient.hentPerson(testIdent, "hentperson-relasjon-dødsfall")
        assertThat(pdlPersonData.forelderBarnRelasjon.size).isEqualTo(1)
        assertThat(pdlPersonData.forelderBarnRelasjon.first().minRolleForPerson).isEqualTo(FORELDERBARNRELASJONROLLE.MOR)
        assertThat(pdlPersonData.forelderBarnRelasjon.first().relatertPersonsRolle).isEqualTo(FORELDERBARNRELASJONROLLE.BARN)
        assertThat(pdlPersonData.dødsfall.first().dødsdato).isEqualTo(LocalDate.of(2021, 1, 14))
        assertThat(pdlPersonData.fødsel.first().fødselsdato).isEqualTo(LocalDate.of(1998, 5, 9))
    }

    @Test
    fun hentPersonFeilerMedInternalServerOgKasterIntegrasjonsException() {
        stubFor(
            post(urlEqualTo("/api/graphql"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(500)
                )
        )

        assertThatThrownBy {
            pdlClient.hentPerson(testIdent, "hentperson-relasjon-dødsfall")
        }.isInstanceOf(IntegrasjonException::class.java)
            .hasMessageContaining("Feil ved oppslag på hentPerson mot PDL. Gav feil: 500 Server Error")
    }

    @Test
    fun hentPersonGraphqlReturnererFeilmeldingOgDetKastesEnIntegrajonsException() {
        mockResponseForPdlQuery(
            pdlRequestBody = gyldigRequest("hentperson-relasjon-dødsfall.graphql", testIdent),
            mockResponse = readfile("mock-error-response.json")
        )

        assertThatThrownBy {
            pdlClient.hentPerson(testIdent, "hentperson-relasjon-dødsfall")
        }.isInstanceOf(IntegrasjonException::class.java)
            .hasMessageContaining("Feil ved oppslag på hentPerson mot PDL: Fant ikke person")
    }

    companion object {

        val testIdent = "12345678901"

        private fun mockResponseForPdlQuery(pdlRequestBody: String, mockResponse: String) {
            stubFor(
                post(urlEqualTo("/api/graphql"))
                    .withRequestBody(equalToJson(pdlRequestBody))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(mockResponse)
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
