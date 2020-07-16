package no.nav.familie.ba.mottak.mapper

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.familie.ba.mottak.repository.domain.Soknad
import no.nav.familie.kontrakter.felles.objectMapper
import main.kotlin.no.nav.familie.ba.søknad.Søknad as Søknadskontrakt


object SøknadMapper {
    inline fun <reified T : Any> toDto(soknad: Soknad): T {
        return objectMapper.readValue(soknad.søknadJson)
    }

    fun fromDto(søknadskontrakt: Søknadskontrakt): Soknad {
        return Soknad(søknadJson = objectMapper.writeValueAsString(søknadskontrakt),
                fnr = "12345678901"
        )
    }
}
