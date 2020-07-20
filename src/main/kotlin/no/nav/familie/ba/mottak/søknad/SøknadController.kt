package no.nav.familie.ba.mottak.søknad

import main.kotlin.no.nav.familie.ba.søknad.Søknad
import no.nav.familie.ba.mottak.config.FeatureToggleService
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping(path = ["/api"], produces = [APPLICATION_JSON_VALUE])
@ProtectedWithClaims(issuer = "selvbetjening", claimMap = ["acr=Level4"])
class SøknadController(val featureToggleService: FeatureToggleService) {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    @PostMapping(value = ["/soknad"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(@RequestPart("søknad") søknad: Søknad): ResponseEntity<Ressurs<Kvittering>> {

        return if (featureToggleService.isEnabled("familie-ba-mottak.lagre-soknad")) {
            log.info("Lagring av søknad er aktivert")
            ResponseEntity.ok(Ressurs.success(Kvittering("Søknad er lagret", LocalDateTime.now())))
        } else {
            log.info("Lagring av søknad er deaktivert")
            ResponseEntity.ok(Ressurs.success(Kvittering("Lagring av søknad er deaktivert", LocalDateTime.now())))
        }
    }
}