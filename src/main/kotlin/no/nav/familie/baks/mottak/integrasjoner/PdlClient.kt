package no.nav.familie.baks.mottak.integrasjoner

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.familie.baks.mottak.domene.personopplysning.Person
import no.nav.familie.felles.tokenklient.entraid.EntraIDRestClientFactory
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.kontrakter.felles.personopplysning.ForelderBarnRelasjon
import no.nav.familie.kontrakter.felles.personopplysning.Oppholdsadresse
import no.nav.familie.kontrakter.felles.personopplysning.SIVILSTANDTYPE
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.LocalDate

@Service
class PdlClient(
    @param:Value("\${PDL_URL}") pdlBaseUrl: URI,
    @param:Value("\${PDL_SCOPE}") private val pdlScope: String,
    entraIDRestClientFactory: EntraIDRestClientFactory,
) {
    private val restClient = entraIDRestClientFactory.lagMaskinTilMaskinRestKlient(pdlScope)
    private val pdlUri =
        UriComponentsBuilder
            .fromUri(pdlBaseUrl)
            .pathSegment(PATH_GRAPHQL)
            .build()
            .toUri()

    fun hentIdenter(
        personIdent: String,
        tema: Tema,
    ): List<IdentInformasjon> {
        val pdlPersonRequest = mapTilPdlPersonRequest(personIdent, hentGraphqlQuery("hentIdenter"))
        val response = post<PdlHentIdenterResponse>(pdlPersonRequest, tema)

        if (response.harFeil()) {
            secureLogger.info("Response mot PDL harFeil for ident=$personIdent response=$response")
            if (response.errors?.any { it.extensions?.notFound() == true } == true) {
                throw PdlNotFoundException(
                    msg = "Fant ikke identer på person: ${response.errorMessages()}",
                    uri = pdlUri,
                    ident = personIdent,
                )
            }
            throw IntegrasjonException(
                msg = "Fant ikke identer på person: ${response.errorMessages()}",
                uri = pdlUri,
                ident = personIdent,
            )
        } else if (response.harAdvarsel()) {
            log.warn("Advarsel ved henting av identer fra PDL. Se securelogs for detaljer.")
            secureLogger.warn("Advarsel ved henting av identer fra PDL: ${response.extensions?.warnings}")
        }
        return response.data.pdlIdenter
            ?.identer
            .orEmpty()
    }

    fun hentPersonident(
        aktørId: String,
        tema: Tema,
    ): String {
        val aktiveIdenter = hentIdenter(aktørId, tema).filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name && !it.historisk }
        if (aktiveIdenter.isNotEmpty()) {
            return aktiveIdenter.last().ident
        } else {
            throw PdlNotFoundException(
                msg = "Fant ikke aktive identer på person",
                uri = pdlUri,
                ident = aktørId,
            )
        }
    }

    fun hentAktørId(
        personIdent: String,
        tema: Tema,
    ): String = hentIdenter(personIdent, tema).filter { it.gruppe == Identgruppe.AKTORID.name && !it.historisk }.last().ident

    fun hentPersonMedRelasjoner(
        personIdent: String,
        tema: Tema,
    ): Person {
        val pdlPersonRequest = mapTilPdlPersonRequest(personIdent, hentGraphqlQuery("hentperson-med-relasjoner"))
        try {
            val response = post<PdlHentPersonResponse>(pdlPersonRequest, tema)
            if (response.harFeil()) {
                throw IntegrasjonException(
                    msg = "Feil ved oppslag på person: ${response.errorMessages()}",
                    uri = pdlUri,
                    ident = personIdent,
                )
            } else if (response.harAdvarsel()) {
                log.warn("Advarsel ved oppslag på person. Se securelogs for detaljer.")
                secureLogger.warn("Advarsel ved oppslag på person: ${response.extensions?.warnings}")
            }
            return Result
                .runCatching {
                    val forelderBarnRelasjoner: Set<ForelderBarnRelasjon> =
                        response.data.person!!
                            .forelderBarnRelasjon
                            .map { relasjon ->
                                ForelderBarnRelasjon(
                                    relatertPersonsIdent = relasjon.relatertPersonsIdent,
                                    relatertPersonsRolle = relasjon.relatertPersonsRolle,
                                )
                            }.toSet()
                    response.data.person.let {
                        Person(
                            navn = null,
                            forelderBarnRelasjoner = forelderBarnRelasjoner,
                            adressebeskyttelseGradering = it.adressebeskyttelse.map { it.gradering },
                            bostedsadresse = it.bostedsadresse.firstOrNull(),
                        )
                    }
                }.fold(
                    onSuccess = { it },
                    onFailure = {
                        throw IntegrasjonException("Fant ikke forespurte data på person.", it, pdlUri, personIdent)
                    },
                )
        } catch (e: Exception) {
            when (e) {
                is IntegrasjonException -> throw e
                else -> throw IntegrasjonException(msg = "Feil ved oppslag på person. Gav feil: ${e.message}", uri = pdlUri, ident = personIdent)
            }
        }
    }

    fun hentPerson(
        personIdent: String,
        graphqlfil: String,
        tema: Tema,
        historikk: Boolean = false,
    ): PdlPersonData {
        val pdlPersonRequest = mapTilPdlPersonRequest(personIdent, hentGraphqlQuery(graphqlfil), historikk)
        val response =
            try {
                post<PdlHentPersonResponse>(pdlPersonRequest, tema)
            } catch (e: Exception) {
                when (e) {
                    is IntegrasjonException -> throw e
                    else -> throw IntegrasjonException(msg = "Feil ved oppslag på hentPerson mot PDL. Gav feil: ${e.message}", uri = pdlUri, ident = personIdent)
                }
            }

        if (response.harFeil()) {
            if (response.errors?.any { it.extensions?.notFound() == true } == true) {
                secureLogger.warn("Fant ikke person med ident: $personIdent i PDL")
                throw PdlNotFoundException("Fant ingen person for ident", pdlUri, personIdent)
            } else {
                throw IntegrasjonException(msg = "Feil ved oppslag på hentPerson mot PDL: ${response.errorMessages()}", uri = pdlUri, ident = personIdent)
            }
        } else if (response.harAdvarsel()) {
            log.warn("Advarsel ved oppslag på hentPerson mot PDL. Se securelogs for detaljer.")
            secureLogger.warn("Advarsel ved oppslag på hentPerson mot PDL: ${response.extensions?.warnings}")
        }
        return response.data.person!!
    }

    private inline fun <reified T : Any> post(
        body: Any,
        tema: Tema,
    ): T =
        restClient
            .post()
            .uri(pdlUri)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header("Tema", TEMA)
            .header("behandlingsnummer", tema.behandlingsnummer)
            .body(body)
            .retrieve()
            .body(T::class.java)!!

    private fun mapTilPdlPersonRequest(
        personIdent: String,
        personInfoQuery: String,
        historikk: Boolean = false,
    ) = mapOf(
        "variables" to
            buildMap {
                put("ident", personIdent)
                if (historikk) put("historikk", true)
            },
        "query" to personInfoQuery,
    )

    private fun hentGraphqlQuery(pdlResource: String): String =
        this::class.java.getResource("/pdl/$pdlResource.graphql").readText().let {
            StringUtils.normalizeSpace(it.replace("\n", ""))
        }

    companion object {
        private val log = LoggerFactory.getLogger(PdlClient::class.java)
        private val secureLogger = LoggerFactory.getLogger("secureLogger")
        private const val PATH_GRAPHQL = "graphql"
        private const val TEMA = "BAR"
    }
}

interface PdlBaseResponse {
    val errors: List<PdlError>?
    val extensions: PdlExtensions?

    fun harFeil() = errors != null && errors!!.isNotEmpty()

    fun harAdvarsel() = !extensions?.warnings.isNullOrEmpty()

    fun errorMessages() = errors?.joinToString { it -> it.message } ?: ""
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlError(
    val message: String,
    val extensions: PdlErrorExtensions?,
)

data class PdlErrorExtensions(
    val code: String?,
) {
    fun notFound() = code == "not_found"
}

data class PdlExtensions(
    val warnings: List<PdlWarning>?,
)

data class PdlWarning(
    val details: Any?,
    val id: String?,
    val message: String?,
    val query: String?,
)

data class PdlHentPersonResponse(
    val data: PdlPerson,
    override val errors: List<PdlError>?,
    override val extensions: PdlExtensions?,
) : PdlBaseResponse

data class PdlHentIdenterResponse(
    val data: Data,
    override val errors: List<PdlError>?,
    override val extensions: PdlExtensions?,
) : PdlBaseResponse

data class Data(
    val pdlIdenter: PdlIdenter?,
)

data class PdlIdenter(
    val identer: List<IdentInformasjon>,
)

data class IdentInformasjon(
    val ident: String,
    val historisk: Boolean,
    val gruppe: String,
)

data class PdlPerson(
    val person: PdlPersonData?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlPersonData(
    val forelderBarnRelasjon: List<PdlForeldreBarnRelasjon> = emptyList(),
    val adressebeskyttelse: List<Adressebeskyttelse> = emptyList(),
    val bostedsadresse: List<Bostedsadresse?> = emptyList(),
    @param:JsonProperty(value = "doedsfall") val dødsfall: List<Dødsfall> = emptyList(),
    @param:JsonProperty(value = "foedselsdato") val fødsel: List<Fødsel> = emptyList(),
    val sivilstand: List<Sivilstand> = emptyList(),
    @param:JsonProperty(value = "foedested") val fødested: List<Fødested> = emptyList(),
    val oppholdsadresse: List<Oppholdsadresse> = emptyList(),
    val falskIdentitet: FalskIdentitet? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlForeldreBarnRelasjon(
    val relatertPersonsIdent: String?,
    val relatertPersonsRolle: FORELDERBARNRELASJONROLLE,
    val minRolleForPerson: FORELDERBARNRELASJONROLLE? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Adressebeskyttelse(
    val gradering: Adressebeskyttelsesgradering,
    val metadata: PdlMetadata? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlMetadata(
    val historisk: Boolean,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Dødsfall(
    @param:JsonProperty(value = "doedsdato") val dødsdato: LocalDate,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Fødsel(
    @param:JsonProperty(value = "foedselsdato") val fødselsdato: LocalDate,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Fødested(
    @param:JsonProperty(value = "foedeland") val fødeland: String,
)

fun Fødested.erUtenforNorge(): Boolean =
    when (this.fødeland) {
        null, "NOR" -> false
        else -> true
    }

@JsonIgnoreProperties(ignoreUnknown = true)
data class Sivilstand(
    val type: SIVILSTANDTYPE,
    val gyldigFraOgMed: LocalDate? = null,
    val bekreftelsesdato: LocalDate? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FalskIdentitet(
    val erFalsk: Boolean? = null,
)

enum class Adressebeskyttelsesgradering {
    STRENGT_FORTROLIG_UTLAND, // Kode 19
    FORTROLIG, // Kode 7
    STRENGT_FORTROLIG, // Kode 6
    UGRADERT,
    ;

    fun erStrengtFortrolig() = this == STRENGT_FORTROLIG || this == STRENGT_FORTROLIG_UTLAND

    fun erFortrolig(): Boolean = this == FORTROLIG
}

enum class Identgruppe {
    AKTORID,
    FOLKEREGISTERIDENT,
    ORGNR,
}
