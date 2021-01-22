package no.nav.familie.ba.mottak.hendelser

import no.nav.familie.ba.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.ba.mottak.e2e.E2EController
import no.nav.familie.prosessering.rest.RestTaskService
import no.nav.familie.sikkerhet.OIDCUtil
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.*
import kotlin.random.Random
import kotlin.random.nextUInt

@RestController
@RequestMapping("/api")
@ProtectedWithClaims(issuer = "azuread")
class HendelseController(private val leesahService: LeesahService) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)


    @PostMapping(path = ["/hendelse/doedsfall"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Unprotected
    fun pdlHendelseDødsfall(@RequestBody personIdenter: List<String>): String {
        logger.info("Oppretter manuell dødshendelse")
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
                offset = Random.nextUInt().toLong(),
                hendelseId = hendelseId,
                personIdenter = personIdenter,
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_DØDSFALL,
                dødsdato = LocalDate.now())

        leesahService.prosesserNyHendelse(pdlHendelse)
        return hendelseId
    }
}