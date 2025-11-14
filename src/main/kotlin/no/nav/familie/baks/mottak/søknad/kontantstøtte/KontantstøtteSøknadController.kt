package no.nav.familie.baks.mottak.søknad.kontantstøtte

import no.nav.familie.baks.mottak.søknad.Kvittering
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.FødselsnummerErNullException
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.ks.søknad.StøttetVersjonertKontantstøtteSøknad
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV4
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV5
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV6
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
import no.nav.familie.kontrakter.ks.søknad.v4.KontantstøtteSøknad as KontantstøtteSøknadKontraktV4
import no.nav.familie.kontrakter.ks.søknad.v5.KontantstøtteSøknad as KontantstøtteSøknadKontraktV5
import no.nav.familie.kontrakter.ks.søknad.v6.KontantstøtteSøknad as KontantstøtteSøknadKontraktV6

@RestController
@RequestMapping(path = ["/api/kontantstotte/"], produces = [APPLICATION_JSON_VALUE])
@ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
class KontantstøtteSøknadController(
    private val kontantstøtteSøknadService: KontantstøtteSøknadService,
    private val kontantstøtteSøknadMetrikkService: KontantstøtteSøknadMetrikkService,
) {
    @PostMapping(value = ["/soknad/v4"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(
        @RequestPart("søknad") søknad: KontantstøtteSøknadKontraktV4,
    ): ResponseEntity<Ressurs<Kvittering>> = mottaVersjonertSøknadOgSendMetrikker(versjonertKontantstøtteSøknad = VersjonertKontantstøtteSøknadV4(kontantstøtteSøknad = søknad))

    @PostMapping(value = ["/soknad/v5"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(
        @RequestPart("søknad") søknad: KontantstøtteSøknadKontraktV5,
    ): ResponseEntity<Ressurs<Kvittering>> = mottaVersjonertSøknadOgSendMetrikker(versjonertKontantstøtteSøknad = VersjonertKontantstøtteSøknadV5(kontantstøtteSøknad = søknad))

    @PostMapping(value = ["/soknad/v6"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(
        @RequestPart("søknad") søknad: KontantstøtteSøknadKontraktV6,
    ): ResponseEntity<Ressurs<Kvittering>> = mottaVersjonertSøknadOgSendMetrikker(versjonertKontantstøtteSøknad = VersjonertKontantstøtteSøknadV6(kontantstøtteSøknad = søknad))

    fun mottaVersjonertSøknadOgSendMetrikker(versjonertKontantstøtteSøknad: StøttetVersjonertKontantstøtteSøknad): ResponseEntity<Ressurs<Kvittering>> =
        try {
            val dbKontantstøtteSøknad =
                kontantstøtteSøknadService.mottaKontantstøtteSøknad(versjonertKontantstøtteSøknad)
            kontantstøtteSøknadMetrikkService.sendMottakMetrikker(versjonertKontantstøtteSøknad)
            ResponseEntity.ok(
                Ressurs.success(
                    Kvittering(
                        "Søknad om kontantstøtte er mottatt",
                        dbKontantstøtteSøknad.opprettetTid,
                    ),
                ),
            )
        } catch (e: FødselsnummerErNullException) {
            kontantstøtteSøknadMetrikkService.sendMottakFeiletMetrikker()
            ResponseEntity.status(500).body(Ressurs.failure("Lagring av søknad om kontantstøtte feilet"))
        }

    @GetMapping(value = ["/ping"])
    @Unprotected
    fun ping(): ResponseEntity<String> = ResponseEntity.ok().body("OK")
}
