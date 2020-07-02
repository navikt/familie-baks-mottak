package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.domene.personopplysning.Familierelasjon
import no.nav.familie.ba.mottak.domene.personopplysning.PersonIdent
import no.nav.familie.ba.mottak.domene.personopplysning.Personinfo
import no.nav.familie.ba.mottak.domene.personopplysning.RelasjonsRolleType
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.personinfo.Ident
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.env.Environment
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import java.net.URI
import java.time.LocalDate

private val logger = LoggerFactory.getLogger(PersonService::class.java)
private val secureLogger = LoggerFactory.getLogger("secureLogger")
private const val OAUTH2_CLIENT_CONFIG_KEY = "integrasjoner-clientcredentials"

@Component
class PersonService @Autowired constructor(@param:Value("\${FAMILIE_INTEGRASJONER_API_URL}")
                                           private val integrasjonerServiceUri: URI,
                                           restTemplateBuilderMedProxy: RestTemplateBuilder,
                                           clientConfigurationProperties: ClientConfigurationProperties,
                                           oAuth2AccessTokenService: OAuth2AccessTokenService,
                                           private val environment: Environment)
    : BaseService(OAUTH2_CLIENT_CONFIG_KEY,
                  restTemplateBuilderMedProxy,
                  clientConfigurationProperties,
                  oAuth2AccessTokenService) {

    @Retryable(value = [RuntimeException::class], maxAttempts = 3, backoff = Backoff(delay = 5000))
    fun hentPersonMedRelasjoner(personIdent: String): Personinfo {
        if (environment.activeProfiles.contains("e2e")) {
            return mockData(personIdent)
        }
        val uri = URI.create("$integrasjonerServiceUri/personopplysning/v2/info")
        logger.info("Henter personinfo fra $uri")
        return try {
            val response = postRequest<Ressurs<Personinfo>, Ident>(uri, Ident(personIdent))
            secureLogger.info("Personinfo for {}: {}", personIdent, response?.body)
            response?.body?.data ?: throw RuntimeException("Response, body eller data er null.")
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
                    fødselsdato = LocalDate.of(1994, 12, 1),
                    relasjonsrolle = RelasjonsRolleType.MORA,
                    harSammeBosted = true)
            else -> Familierelasjon(
                    personIdent = PersonIdent("12345678901"),
                    fødselsdato = LocalDate.of(1980, 9, 10),
                    relasjonsrolle = RelasjonsRolleType.MORA,
                    harSammeBosted = true)
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
