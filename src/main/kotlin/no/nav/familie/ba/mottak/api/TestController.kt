package no.nav.familie.ba.mottak.api

import no.nav.familie.ba.mottak.config.VaultServiceUser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api/"], produces = [MediaType.APPLICATION_JSON_VALUE])
class PingController(@Autowired val vaultServiceUser: VaultServiceUser) {

    @GetMapping("/ping")
    fun ping(): String {
        return hentSrvBruker()
    }

    fun hentSrvBruker(): String {
        return vaultServiceUser.serviceuserUsername
    }

}