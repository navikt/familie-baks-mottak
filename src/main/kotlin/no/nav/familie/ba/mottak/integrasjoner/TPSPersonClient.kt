package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.domene.personopplysning.Familierelasjon
import no.nav.familie.ba.mottak.domene.personopplysning.PersonIdent
import no.nav.familie.ba.mottak.domene.personopplysning.Personinfo
import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.getDataOrThrow
import no.nav.familie.kontrakter.felles.personopplysning.Ident
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestOperations
import java.net.URI
import java.time.LocalDate

private val logger = LoggerFactory.getLogger(TPSPersonClient::class.java)
private val secureLogger = LoggerFactory.getLogger("secureLogger")
private const val OAUTH2_CLIENT_CONFIG_KEY = "integrasjoner-clientcredentials"

@Deprecated("Fjernes når barnetrygd er ute av infotrygd")
@Component
class TPSPersonClient @Autowired constructor(@param:Value("\${FAMILIE_INTEGRASJONER_API_URL}")
                                             private val integrasjonerServiceUri: URI,
                                             @Qualifier("clientCredentials") restOperations: RestOperations,
                                             private val environment: Environment)
    : AbstractRestClient(restOperations, "integrasjon.tps") {

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun hentPersonMedRelasjoner(personIdent: String): Personinfo {
        if (environment.activeProfiles.contains("e2e")) {
            return mockData(personIdent)
        }
        val uri = URI.create("$integrasjonerServiceUri/personopplysning/v2/info")
        logger.info("Henter personinfo fra $uri")
        return try {
            val response = postForEntity<Ressurs<Personinfo>>(uri, Ident(personIdent))
            secureLogger.info("Personinfo for {}: {}", personIdent, response)
            response.getDataOrThrow()
        } catch (e: HttpStatusCodeException) {
            logger.info("Feil mot TPS. status=${e.statusCode}, stacktrace=${e.stackTrace.toList()}")
            secureLogger.info("Feil mot TPS. msg=${e.message}, body=${e.responseBodyAsString}")
            throw RuntimeException("Kall mot integrasjon feilet ved uthenting av personinfo. ${e.statusCode} ${e.responseBodyAsString}")
        }
    }

    private fun mockData(personIdent: String): Personinfo {

        val mor = when (personIdent) {
            "01062000001" -> Familierelasjon(
                    personIdent = PersonIdent("01129400001"),
                    relasjonsrolle = "MOR")
            else -> Familierelasjon(
                    personIdent = PersonIdent("12345678901"),
                    relasjonsrolle = "MOR")
        }

        return Personinfo(
                personIdent = PersonIdent(personIdent),
                navn = "E2E Mockesen",
                bostedsadresse = null,
                kjønn = null,
                fødselsdato = LocalDate.now(),
                dødsdato = null,
                personstatus = null,
                familierelasjoner = setOf(mor),
                statsborgerskap = null,
                utlandsadresse = null,
                geografiskTilknytning = null,
                diskresjonskode = null,
                adresseLandkode = null,
                sivilstand = null)

    }
}
