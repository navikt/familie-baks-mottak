package no.nav.familie.ba.mottak.søknad

import main.kotlin.no.nav.familie.ba.søknad.Søknad
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE

@RestController
@RequestMapping(path = ["/api"], produces = [APPLICATION_JSON_VALUE])
@Unprotected
class SøknadController {
    @PostMapping(value= ["/soknad"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(@RequestBody(required = true) søknad: Søknad): ResponseEntity<String> {
        return ResponseEntity.ok("Søknad mottatt OK")
    }
}