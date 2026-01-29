package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.domene.personopplysning.Person
import no.nav.familie.kontrakter.felles.Tema
import org.springframework.cache.annotation.Cacheable
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service

@Service
class PdlClientService(
    private val pdlClient: PdlClient,
) {
    @Retryable(value = [RuntimeException::class], maxRetries = 3, delayString = ("\${retry.backoff.delay:5000}"))
    @Cacheable("hentIdenter", cacheManager = "hourlyCacheManager")
    fun hentIdenter(
        personIdent: String,
        tema: Tema,
    ): List<IdentInformasjon> = pdlClient.hentIdenter(personIdent, tema)

    @Retryable(value = [RuntimeException::class], maxRetries = 3, delayString = ("\${retry.backoff.delay:5000}"), excludes = [PdlNotFoundException::class])
    @Cacheable("hentIdenter", cacheManager = "hourlyCacheManager")
    fun hentPersonident(
        aktørId: String,
        tema: Tema,
    ): String = pdlClient.hentPersonident(aktørId, tema)

    @Retryable(value = [RuntimeException::class], maxRetries = 3, delayString = ("\${retry.backoff.delay:5000}"), excludes = [PdlNotFoundException::class])
    @Cacheable("hentIdenter", cacheManager = "hourlyCacheManager")
    fun hentAktørId(
        personIdent: String,
        tema: Tema,
    ): String = pdlClient.hentAktørId(personIdent, tema)

    fun hentPersonMedRelasjoner(
        personIdent: String,
        tema: Tema,
    ): Person = pdlClient.hentPersonMedRelasjoner(personIdent, tema)

    fun hentPerson(
        personIdent: String,
        graphqlfil: String,
        tema: Tema,
    ): PdlPersonData = pdlClient.hentPerson(personIdent, graphqlfil, tema)
}
