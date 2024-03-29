package no.nav.familie.baks.mottak.søknad.barnetrygd

import no.nav.familie.baks.mottak.søknad.Kvittering
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.FødselsnummerErNullException
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV7
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.VersjonertSøknad
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
import no.nav.familie.kontrakter.ba.søknad.v7.Søknad as SøknadKontraktV7
import no.nav.familie.kontrakter.ba.søknad.v8.Søknad as SøknadKontraktV8

@RestController
@RequestMapping(path = ["/api"], produces = [APPLICATION_JSON_VALUE])
@ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
class BarnetrygdSøknadController(
    private val barnetrygdSøknadService: BarnetrygdSøknadService,
    private val barnetrygdSøknadMetrikkService: BarnetrygdSøknadMetrikkService,
) {
    @PostMapping(value = ["/soknad/v7"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(
        @RequestPart("søknad") søknad: SøknadKontraktV7,
    ): ResponseEntity<Ressurs<Kvittering>> =
        mottaVersjonertSøknadOgSendMetrikker(versjonertSøknad = SøknadV7(søknad = søknad))

    @PostMapping(value = ["/soknad/v8"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(
        @RequestPart("søknad") søknad: SøknadKontraktV8,
    ): ResponseEntity<Ressurs<Kvittering>> =
        mottaVersjonertSøknadOgSendMetrikker(versjonertSøknad = SøknadV8(søknad = søknad))

    fun mottaVersjonertSøknadOgSendMetrikker(versjonertSøknad: VersjonertSøknad): ResponseEntity<Ressurs<Kvittering>> {
        return try {
            val dbSøknad = barnetrygdSøknadService.motta(versjonertSøknad = versjonertSøknad)
            barnetrygdSøknadMetrikkService.sendMottakMetrikker(versjonertSøknad)
            ResponseEntity.ok(Ressurs.success(Kvittering("Søknad er mottatt", dbSøknad.opprettetTid)))
        } catch (e: FødselsnummerErNullException) {
            barnetrygdSøknadMetrikkService.sendMottakFeiletMetrikker(versjonertSøknad)
            ResponseEntity.status(500).body(Ressurs.failure("Lagring av søknad feilet"))
        }
    }

    @GetMapping(value = ["/ping"])
    @Unprotected
    fun ping(): ResponseEntity<String> {
        return ResponseEntity.ok().body("OK")
    }
}
