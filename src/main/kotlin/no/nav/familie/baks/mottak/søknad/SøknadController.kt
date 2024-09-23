package no.nav.familie.baks.mottak.søknad

import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.ADRESSEBESKYTTELSEGRADERING
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api/hent-soknad/"], produces = [APPLICATION_JSON_VALUE])
@ProtectedWithClaims(issuer = "azuread")
class SøknadController {
    @GetMapping(value = ["/hent-adressebeskyttelse/{tema}/{journalpostId}"])
    fun hentStrengesteAdressebeskyttelsegraderingIDigitalSøknad(
        @RequestParam("tema") tema: Tema,
        @RequestParam("journalpostId") journalpostId: String,
    ): ResponseEntity<ADRESSEBESKYTTELSEGRADERING> = ResponseEntity.ok(ADRESSEBESKYTTELSEGRADERING.UGRADERT)
}
