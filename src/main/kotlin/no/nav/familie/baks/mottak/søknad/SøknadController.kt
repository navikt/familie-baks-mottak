package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.integrasjoner.SøknadsidenterService
import no.nav.familie.kontrakter.felles.Tema
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api/soknad/"], produces = [APPLICATION_JSON_VALUE])
@ProtectedWithClaims(issuer = "azuread")
class SøknadController(
    private val søknadsidenterService: SøknadsidenterService
) {
    @GetMapping(value = ["/hent-personer-i-digital-soknad/{tema}/{journalpostId}"])
    fun hentPersonerIDigitalSøknad(
        @PathVariable("tema") tema: Tema,
        @PathVariable("journalpostId") journalpostId: String,
    ): ResponseEntity<List<String>> {
        val identerIDigitalSøknad = søknadsidenterService.hentIdenterIDigitalSøknadFraJournalpost(tema, journalpostId)
        return ResponseEntity.ok(identerIDigitalSøknad)
    }
}
