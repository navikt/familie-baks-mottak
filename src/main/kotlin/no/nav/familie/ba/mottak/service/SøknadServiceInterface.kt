package no.nav.familie.ba.mottak.service

import no.nav.familie.ba.mottak.repository.domain.Soknad

interface SøknadServiceInterface {

    fun motta(soknad: Soknad): String

    fun get(id: String): Soknad

    fun sendTilSak(søknadId: String)

    fun lagreSøknad(soknad: Soknad)


}