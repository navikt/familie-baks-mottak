package no.nav.familie.baks.mottak.integrasjoner

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.familie.baks.mottak.domene.personopplysning.Person
import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.http.util.UriUtil
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.kontrakter.felles.personopplysning.ForelderBarnRelasjon
import no.nav.familie.kontrakter.felles.personopplysning.SIVILSTAND
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class PdlClient(
    @Value("\${PDL_URL}") pdlBaseUrl: URI,
    @Qualifier("clientCredentials") val restTemplate: RestOperations
) : AbstractRestClient(restTemplate, "pdl.personinfo") {

    private val pdlUri = UriUtil.uri(pdlBaseUrl, PATH_GRAPHQL)

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delayExpression = "\${retry.backoff.delay:5000}"))
    @Cacheable("hentIdenter", cacheManager = "hourlyCacheManager")
    fun hentIdenter(personIdent: String): List<IdentInformasjon> {
        val pdlPersonRequest = mapTilPdlPersonRequest(personIdent, hentGraphqlQuery("hentIdenter"))
        val response = postForEntity<PdlHentIdenterResponse>(pdlUri, pdlPersonRequest, httpHeaders())

        if (!response.harFeil()) return response.data.pdlIdenter?.identer.orEmpty()
        throw IntegrasjonException(
            msg = "Fant ikke identer på person: ${response.errorMessages()}",
            uri = pdlUri,
            ident = personIdent
        )
    }

    fun hentPersonident(aktørId: String): String {
        return hentIdenter(aktørId).filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name && !it.historisk }.last().ident
    }

    fun hentAktørId(personIdent: String): String {
        return hentIdenter(personIdent).filter { it.gruppe == Identgruppe.AKTORID.name && !it.historisk }.last().ident
    }

    fun hentPersonMedRelasjoner(personIdent: String): Person {
        val pdlPersonRequest = mapTilPdlPersonRequest(personIdent, hentGraphqlQuery("hentperson-med-relasjoner"))

        try {
            val response = postForEntity<PdlHentPersonResponse>(
                pdlUri,
                pdlPersonRequest,
                httpHeaders()
            )
            if (!response.harFeil()) {
                return Result.runCatching {
                    val forelderBarnRelasjoner: Set<ForelderBarnRelasjon> =
                        response.data.person!!.forelderBarnRelasjon.map { relasjon ->
                            ForelderBarnRelasjon(
                                relatertPersonsIdent = relasjon.relatertPersonsIdent,
                                relatertPersonsRolle = relasjon.relatertPersonsRolle
                            )
                        }.toSet()

                    response.data.person.let {
                        Person(
                            navn = null,
                            forelderBarnRelasjoner = forelderBarnRelasjoner,
                            adressebeskyttelseGradering = it.adressebeskyttelse.firstOrNull()?.gradering?.name,
                            bostedsadresse = it.bostedsadresse.firstOrNull()
                        )
                    }
                }.fold(
                    onSuccess = { it },
                    onFailure = {
                        throw IntegrasjonException(
                            "Fant ikke forespurte data på person.",
                            it,
                            pdlUri,
                            personIdent
                        )
                    }
                )
            } else {
                throw IntegrasjonException(
                    msg = "Feil ved oppslag på person: ${response.errorMessages()}",
                    uri = pdlUri,
                    ident = personIdent
                )
            }
        } catch (e: Exception) {
            when (e) {
                is IntegrasjonException -> throw e
                else -> throw IntegrasjonException(
                    msg = "Feil ved oppslag på person. Gav feil: ${e.message}",
                    uri = pdlUri,
                    ident = personIdent
                )
            }
        }
    }

    fun hentPerson(personIdent: String, graphqlfil: String): PdlPersonData {
        val pdlPersonRequest = mapTilPdlPersonRequest(personIdent, hentGraphqlQuery(graphqlfil))

        val response = try {
            postForEntity<PdlHentPersonResponse>(
                pdlUri,
                pdlPersonRequest,
                httpHeaders()
            )
        } catch (e: Exception) {
            when (e) {
                is IntegrasjonException -> throw e
                else -> throw IntegrasjonException(
                    msg = "Feil ved oppslag på hentPerson mot PDL. Gav feil: ${e.message}",
                    uri = pdlUri,
                    ident = personIdent
                )
            }
        }

        if (!response.harFeil()) {
            return response.data.person!!
        } else {
            throw IntegrasjonException(
                msg = "Feil ved oppslag på hentPerson mot PDL: ${response.errorMessages()}",
                uri = pdlUri,
                ident = personIdent
            )
        }
    }

    private fun mapTilPdlPersonRequest(
        personIdent: String,
        personInfoQuery: String
    ) = mapOf(
        "variables" to mapOf("ident" to personIdent),
        "query" to personInfoQuery
    )

    protected fun httpHeaders(): HttpHeaders {
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            accept = listOf(MediaType.APPLICATION_JSON)
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

data class PdlHentPersonResponse(
    val data: PdlPerson,
    override val errors: List<PdlError>?
) : PdlBaseResponse

data class PdlHentIdenterResponse(
    val data: Data,
    override val errors: List<PdlError>?
) : PdlBaseResponse

data class Data(val pdlIdenter: PdlIdenter?)

data class PdlIdenter(val identer: List<IdentInformasjon>)

data class IdentInformasjon(
    val ident: String,
    val historisk: Boolean,
    val gruppe: String
)

data class PdlPerson(val person: PdlPersonData?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlPersonData(
    val forelderBarnRelasjon: List<PdlForeldreBarnRelasjon> = emptyList(),
    val adressebeskyttelse: List<Adressebeskyttelse> = emptyList(),
    val bostedsadresse: List<Bostedsadresse?> = emptyList(),
    @JsonProperty(value = "doedsfall") val dødsfall: List<Dødsfall> = emptyList(),
    @JsonProperty(value = "foedsel") val fødsel: List<Fødsel> = emptyList(),
    val sivilstand: List<Sivilstand> = emptyList(),
    val innflyttingTilNorge: List<InnflyttingTilNorge> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlForeldreBarnRelasjon(
    val relatertPersonsIdent: String?,
    val relatertPersonsRolle: FORELDERBARNRELASJONROLLE,
    val minRolleForPerson: FORELDERBARNRELASJONROLLE? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Adressebeskyttelse(
    val gradering: Adressebeskyttelsesgradering
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Dødsfall(
    @JsonProperty(value = "doedsdato") val dødsdato: LocalDate
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Fødsel(
    @JsonProperty(value = "foedselsdato") val fødselsdato: LocalDate
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Sivilstand(
    val type: SIVILSTAND,
    val gyldigFraOgMed: LocalDate? = null,
    val bekreftelsesdato: LocalDate? = null
)

data class InnflyttingTilNorge(
    val fraflyttingsland: String?,
    val fraflyttingsstedIUtlandet: String?,
    val folkeregistermetadata: Folkeregistermetadata
)

data class Folkeregistermetadata(
    val ajourholdstidspunkt: LocalDateTime?,
    val gyldighetstidspunkt: LocalDateTime?
)

enum class Adressebeskyttelsesgradering {
    STRENGT_FORTROLIG_UTLAND, // Kode 19
    FORTROLIG, // Kode 7
    STRENGT_FORTROLIG, // Kode 6
    UGRADERT
}

enum class Identgruppe {
    AKTORID,
    FOLKEREGISTERIDENT,
    ORGNR
}