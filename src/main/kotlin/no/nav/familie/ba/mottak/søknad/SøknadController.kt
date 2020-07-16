package no.nav.familie.ba.mottak.søknad

import main.kotlin.no.nav.familie.ba.søknad.Søknad
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(path = ["/api"], produces = [APPLICATION_JSON_VALUE])
@ProtectedWithClaims(issuer = "selvbetjening", claimMap = ["acr=Level4"])
class SøknadController {
    @PostMapping(value= ["/soknad"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(@RequestPart("søknad") søknad: Søknad): ResponseEntity<Kvittering> {
        return ResponseEntity.ok(Kvittering("", "Søknad er registrert mottatt"))
    }
}