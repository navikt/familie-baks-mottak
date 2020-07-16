package no.nav.familie.ba.mottak.service

import main.kotlin.no.nav.familie.ba.søknad.Søknad
import no.nav.familie.ba.mottak.repository.domain.Soknad

interface SøknadServiceInterface {

    fun motta(soknad: Søknad): String

    fun get(id: String): Soknad

    fun lagreSøknad(soknad: Soknad)
}