package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.domene.personopplysning.Familierelasjon
import no.nav.familie.ba.mottak.domene.personopplysning.Person
import no.nav.familie.ba.mottak.domene.personopplysning.PersonIdent
import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.http.sts.StsRestClient
import no.nav.familie.http.util.UriUtil
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import java.net.URI
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse

@Service
class PdlClient(@Value("\${PDL_URL}") pdlBaseUrl: URI,
                @Qualifier("sts") val restTemplate: RestOperations,
                private val stsRestClient: StsRestClient)
    : AbstractRestClient(restTemplate, "pdl.personinfo") {

    private val pdlUri = UriUtil.uri(pdlBaseUrl, PATH_GRAPHQL)

    fun hentIdenter(personIdent: String): List<IdentInformasjon> {
        val pdlPersonRequest = mapTilPdlPersonRequest(personIdent, hentGraphqlQuery("hentIdenter"))
        val response = postForEntity<PdlHentIdenterResponse>(pdlUri, pdlPersonRequest, httpHeaders())

        if (!response.harFeil()) return response.data.pdlIdenter?.identer.orEmpty()
        throw IntegrasjonException(msg = "Fant ikke identer på person: ${response.errorMessages()}",
                                   uri = pdlUri,
                                   ident = personIdent)
    }

    fun hentPersonMedRelasjoner(personIdent: String): Person {

        val pdlPersonRequest = mapTilPdlPersonRequest(personIdent, hentGraphqlQuery("hentperson-med-relasjoner"))

        try {
            val response = postForEntity<PdlHentPersonResponse>(pdlUri,
                                                                pdlPersonRequest,
                                                                httpHeaders())
            if (!response.harFeil()) {
                return Result.runCatching {
                    val familierelasjoner: Set<Familierelasjon> =
                                    response.data.person!!.familierelasjoner.map { relasjon ->
                                        Familierelasjon(personIdent = PersonIdent(id = relasjon.relatertPersonsIdent),
                                                        relasjonsrolle = relasjon.relatertPersonsRolle.name)
                                    }.toSet()

                    response.data.person.let {
                        Person(navn = null,
                               familierelasjoner = familierelasjoner,
                               adressebeskyttelseGradering = it.adressebeskyttelse.firstOrNull()?.gradering?.name,
                               bostedsadresse = it.bostedsadresse.firstOrNull())
                    }
                }.fold(
                        onSuccess = { it },
                        onFailure = {
                            throw IntegrasjonException("Fant ikke forespurte data på person.",
                                                       it,
                                                       pdlUri,
                                                       personIdent)
                        }
                )
            } else {
                throw IntegrasjonException(msg = "Feil ved oppslag på person: ${response.errorMessages()}",
                                           uri = pdlUri,
                                           ident = personIdent)
            }
        } catch (e: Exception) {
            when (e) {
                is IntegrasjonException -> throw e
                else -> throw IntegrasjonException(msg = "Feil ved oppslag på person. Gav feil: ${e.message}",
                                                   uri = pdlUri,
                                                   ident = personIdent)
            }
        }
    }

    private fun mapTilPdlPersonRequest(personIdent: String,
                                       personInfoQuery: String) = mapOf("variables" to mapOf("ident" to personIdent),
                                                                        "query" to personInfoQuery)

    protected fun httpHeaders(): HttpHeaders {
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            accept = listOf(MediaType.APPLICATION_JSON)
            add("Nav-Consumer-Token", "Bearer ${stsRestClient.systemOIDCToken}")
            add("Tema", TEMA)
        }
    }

    private fun hentGraphqlQuery(pdlResource: String): String {
        return this::class.java.getResource("/pdl/$pdlResource.graphql").readText().let {
            StringUtils.normalizeSpace(it.replace("\n", ""))
        }
    }

    companion object {
        private const val PATH_GRAPHQL = "graphql"
        private const val TEMA = "BAR"
    }
}

interface PdlBaseResponse {
    val errors: List<PdlError>?

    fun harFeil() = errors != null && errors!!.isNotEmpty()
    fun errorMessages() = errors?.joinToString { it -> it.message } ?: ""
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlError(val message: String)

data class PdlHentPersonResponse(val data: PdlPerson,
                                 override val errors: List<PdlError>?): PdlBaseResponse

data class PdlHentIdenterResponse(val data: Data,
                                  override val errors: List<PdlError>?): PdlBaseResponse

data class Data(val pdlIdenter: PdlIdenter?)

data class PdlIdenter(val identer: List<IdentInformasjon>)

data class IdentInformasjon(val ident: String,
                            val historisk: Boolean,
                            val gruppe: String)

data class PdlPerson(val person: PdlPersonData?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlPersonData( val familierelasjoner: List<PdlFamilierelasjon> = emptyList(),
                         val adressebeskyttelse: List<Adressebeskyttelse>,
                         val bostedsadresse: List<Bostedsadresse?>,)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlFamilierelasjon(val relatertPersonsIdent: String,
                              val relatertPersonsRolle: Familierelasjonsrolle)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Adressebeskyttelse(
        val gradering: Adressebeskyttelsesgradering
)

enum class Familierelasjonsrolle {
    BARN,
    FAR,
    MEDMOR,
    MOR
}

enum class Adressebeskyttelsesgradering {
    STRENGT_FORTROLIG_UTLAND, // Kode 19
    FORTROLIG, // Kode 7
    STRENGT_FORTROLIG, // Kode 6
    UGRADERT
}

enum class Identgruppe {
    AKTOERID,
    FOLKEREGISTERIDENT,
    ORGNR,
}