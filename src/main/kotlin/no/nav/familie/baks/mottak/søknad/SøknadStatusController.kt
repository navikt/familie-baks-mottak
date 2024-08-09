package no.nav.familie.baks.mottak.søknad

import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api/status"], produces = [MediaType.APPLICATION_JSON_VALUE])
class SøknadStatusController(
    val søknadStatusService: SøknadStatusService,
) {
    @GetMapping(value = ["/barnetrygd"])
    @Unprotected
    fun statusBarnetrygd(): StatusDto = søknadStatusService.statusBarnetrygd()

    @GetMapping(value = ["/kontantstotte"])
    @Unprotected
    fun statusKontantstøtte(): StatusDto = søknadStatusService.statusKontantstøtte()
}
