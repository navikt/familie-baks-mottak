package no.nav.familie.baks.mottak.søknad

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
    fun statusBarnetrygd(): StatusDto = søknadStatusService.statusBarnetrygd()

    @GetMapping(value = ["/kontantstotte"])
    fun statusKontantstøtte(): StatusDto = søknadStatusService.statusKontantstøtte()
}
