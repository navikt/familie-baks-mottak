package no.nav.familie.baks.mottak.søknad

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadObjectMapperModule
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV7
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.VersjonertSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteObjectMapperModule
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV2
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV3
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.VersjonertKontantstøtteSøknad
import org.springframework.stereotype.Service
import no.nav.familie.kontrakter.felles.objectMapper as getObjectMapper

@Service
class SøknadSpråkvelgerService {

    fun konverterBarnetrygdSøknadTilMapForSpråk(versjonertSøknad: VersjonertSøknad, språk: String): Map<String, Any> {
        val objectMapperForSpråk = hentObjectMapperForSpråk(språk)

        val barnetrygdSøknadMapForSpråk = objectMapperForSpråk.convertValue<MutableMap<String, Any>>(
            when (versjonertSøknad) {
                is SøknadV7 -> versjonertSøknad.søknad
                is SøknadV8 -> versjonertSøknad.søknad
            },
        )
        barnetrygdSøknadMapForSpråk["teksterUtenomSpørsmål"] = when (versjonertSøknad) {
            is SøknadV7 -> versjonertSøknad.søknad.teksterUtenomSpørsmål
            is SøknadV8 -> versjonertSøknad.søknad.teksterUtenomSpørsmål
        }.mapValues { it.value[språk] }

        return barnetrygdSøknadMapForSpråk
    }

    fun konverterKontantstøtteSøknadTilMapForSpråk(
        versjonertSøknad: VersjonertKontantstøtteSøknad,
        språk: String,
    ): Map<String, Any> {
        val objectMapperForSpråk = hentObjectMapperForSpråk(språk)

        val kontantstøtteSøknadMapForSpråk = objectMapperForSpråk.convertValue<MutableMap<String, Any>>(
            when (versjonertSøknad) {
                is KontantstøtteSøknadV2 -> versjonertSøknad.kontantstøtteSøknad
                is KontantstøtteSøknadV3 -> versjonertSøknad.kontantstøtteSøknad
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
