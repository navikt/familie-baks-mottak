package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.journalføring.AdressebeskyttelesesgraderingService
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.ADRESSEBESKYTTELSEGRADERING
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
    private val journalpostClient: JournalpostClient,
    private val adressebeskyttelesesgraderingService: AdressebeskyttelesesgraderingService,
) {
    @GetMapping(value = ["/adressebeskyttelse/{tema}/{journalpostId}"])
    fun hentStrengesteAdressebeskyttelsegraderingIDigitalSøknad(
        @PathVariable("tema") tema: Tema,
        @PathVariable("journalpostId") journalpostId: String,
    ): ResponseEntity<ADRESSEBESKYTTELSEGRADERING?> {
        val journalpost = journalpostClient.hentJournalpost(journalpostId = journalpostId)
        val strengesteAdressebeskyttelsesgradering = adressebeskyttelesesgraderingService.finnStrengesteAdressebeskyttelsegraderingPåJournalpost(tema = tema, journalpost = journalpost)
        return ResponseEntity.ok(strengesteAdressebeskyttelsesgradering)
    }
}
