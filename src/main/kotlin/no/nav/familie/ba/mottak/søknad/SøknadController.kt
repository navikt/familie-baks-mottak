package no.nav.familie.ba.mottak.søknad

import main.kotlin.no.nav.familie.ba.søknad.Søknad
import no.nav.familie.ba.mottak.søknad.domene.FødselsnummerErNullException
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.familie.ba.mottak.config.FeatureToggleService
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
        val lagreSøknad = featureToggleService.isEnabled("familie-ba-mottak.lagre-soknad")
        log.info("Lagring av søknad = $lagreSøknad")

        return if (lagreSøknad) {
            return try {
                søknadService.motta(søknad)
                ResponseEntity.ok(Ressurs.success(Kvittering("Søknad er mottatt", LocalDateTime.now())))
            } catch (e: FødselsnummerErNullException) {
                ResponseEntity.status(500).body(Ressurs.failure("Lagring av søknad feilet"))
        } else {
            ResponseEntity.ok(Ressurs.success(Kvittering("Søknad er mottatt. Lagring er deaktivert.", LocalDateTime.now())))
        }
    }
}