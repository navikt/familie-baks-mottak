package no.nav.familie.baks.mottak.søknad.barnetrygd

import no.nav.familie.baks.mottak.søknad.Kvittering
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.FødselsnummerErNullException
import no.nav.familie.kontrakter.ba.søknad.StøttetVersjonertBarnetrygdSøknad
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV8
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV9
import no.nav.familie.kontrakter.ba.søknad.v8.Søknad
import no.nav.familie.kontrakter.ba.søknad.v9.BarnetrygdSøknad
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api"], produces = [APPLICATION_JSON_VALUE])
@ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
class BarnetrygdSøknadController(
    private val barnetrygdSøknadService: BarnetrygdSøknadService,
    private val barnetrygdSøknadMetrikkService: BarnetrygdSøknadMetrikkService,
) {
    @PostMapping(value = ["/soknad/v8"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(
        @RequestPart("søknad") søknad: Søknad,
    ): ResponseEntity<Ressurs<Kvittering>> =
        mottaVersjonertSøknadOgSendMetrikker(
            versjonertBarnetrygdSøknad = VersjonertBarnetrygdSøknadV8(barnetrygdSøknad = søknad),
        )

    @PostMapping(value = ["/soknad/v9"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(
        @RequestPart("søknad") søknad: BarnetrygdSøknad,
    ): ResponseEntity<Ressurs<Kvittering>> =
        mottaVersjonertSøknadOgSendMetrikker(
            versjonertBarnetrygdSøknad = VersjonertBarnetrygdSøknadV9(barnetrygdSøknad = søknad),
        )

    fun mottaVersjonertSøknadOgSendMetrikker(versjonertBarnetrygdSøknad: StøttetVersjonertBarnetrygdSøknad): ResponseEntity<Ressurs<Kvittering>> =
        try {
            val dbSøknad = barnetrygdSøknadService.motta(versjonertBarnetrygdSøknad = versjonertBarnetrygdSøknad)
            barnetrygdSøknadMetrikkService.sendMottakMetrikker(versjonertBarnetrygdSøknad)
            ResponseEntity.ok(Ressurs.success(Kvittering("Søknad er mottatt", dbSøknad.opprettetTid)))
        } catch (e: FødselsnummerErNullException) {
            barnetrygdSøknadMetrikkService.sendMottakFeiletMetrikker(versjonertBarnetrygdSøknad)
            ResponseEntity.status(500).body(Ressurs.failure("Lagring av søknad feilet"))
        }

    @GetMapping(value = ["/ping"])
    @Unprotected
    fun ping(): ResponseEntity<String> = ResponseEntity.ok().body("OK")
}
