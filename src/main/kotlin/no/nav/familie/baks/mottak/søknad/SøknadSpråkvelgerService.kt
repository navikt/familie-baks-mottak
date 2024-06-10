package no.nav.familie.baks.mottak.søknad

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadObjectMapperModule
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.VersjonertBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteObjectMapperModule
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV4
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.VersjonertKontantstøtteSøknad
import org.springframework.stereotype.Service
import no.nav.familie.kontrakter.felles.objectMapper as getObjectMapper

@Service
class SøknadSpråkvelgerService {
    fun konverterBarnetrygdSøknadTilMapForSpråk(
        versjonertBarnetrygdSøknad: VersjonertBarnetrygdSøknad,
        språk: String,
    ): Map<String, Any> {
        val objectMapperForSpråk = hentObjectMapperForSpråk(språk)

        val barnetrygdSøknadMapForSpråk =
            objectMapperForSpråk.convertValue<MutableMap<String, Any>>(
                when (versjonertBarnetrygdSøknad) {
                    is SøknadV8 -> versjonertBarnetrygdSøknad.søknad
                },
            )
        barnetrygdSøknadMapForSpråk["teksterUtenomSpørsmål"] =
            when (versjonertBarnetrygdSøknad) {
                is SøknadV8 -> versjonertBarnetrygdSøknad.søknad.teksterUtenomSpørsmål
            }.mapValues { it.value[språk] }

        return barnetrygdSøknadMapForSpråk
    }

    fun konverterKontantstøtteSøknadTilMapForSpråk(
        versjonertSøknad: VersjonertKontantstøtteSøknad,
        språk: String,
    ): Map<String, Any> {
        val objectMapperForSpråk = hentObjectMapperForSpråk(språk)

        val kontantstøtteSøknadMapForSpråk =
            objectMapperForSpråk.convertValue<MutableMap<String, Any>>(
                when (versjonertSøknad) {
                    is KontantstøtteSøknadV4 -> versjonertSøknad.kontantstøtteSøknad
                },
            )
        return kontantstøtteSøknadMapForSpråk
    }

    fun hentObjectMapperForSpråk(språk: String): ObjectMapper {
        return getObjectMapper.registerModules(
            BarnetrygdSøknadObjectMapperModule(språk),
            KontantstøtteObjectMapperModule(språk),
        )
    }
}
