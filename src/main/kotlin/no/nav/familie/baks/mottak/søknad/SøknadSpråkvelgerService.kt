package no.nav.familie.baks.mottak.søknad

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadObjectMapperModule
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV9
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.VersjonertBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteObjectMapperModule
import no.nav.familie.kontrakter.ks.søknad.StøttetVersjonertKontantstøtteSøknad
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV4
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV5
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
                    is BarnetrygdSøknadV8 -> versjonertBarnetrygdSøknad.barnetrygdSøknad
                    is BarnetrygdSøknadV9 -> versjonertBarnetrygdSøknad.barnetrygdSøknad
                },
            )
        barnetrygdSøknadMapForSpråk["teksterUtenomSpørsmål"] =
            when (versjonertBarnetrygdSøknad) {
                is BarnetrygdSøknadV8 -> versjonertBarnetrygdSøknad.barnetrygdSøknad.teksterUtenomSpørsmål
                is BarnetrygdSøknadV9 -> versjonertBarnetrygdSøknad.barnetrygdSøknad.teksterUtenomSpørsmål
            }.mapValues { it.value[språk] }

        return barnetrygdSøknadMapForSpråk
    }

    fun konverterKontantstøtteSøknadTilMapForSpråk(
        versjonertSøknad: StøttetVersjonertKontantstøtteSøknad,
        språk: String,
    ): Map<String, Any> {
        val objectMapperForSpråk = hentObjectMapperForSpråk(språk)

        val kontantstøtteSøknadMapForSpråk =
            objectMapperForSpråk.convertValue<MutableMap<String, Any>>(
                when (versjonertSøknad) {
                    is VersjonertKontantstøtteSøknadV4 -> versjonertSøknad.kontantstøtteSøknad
                    is VersjonertKontantstøtteSøknadV5 -> versjonertSøknad.kontantstøtteSøknad
                },
            )
        return kontantstøtteSøknadMapForSpråk
    }

    fun hentObjectMapperForSpråk(språk: String): ObjectMapper =
        getObjectMapper.registerModules(
            BarnetrygdSøknadObjectMapperModule(språk),
            KontantstøtteObjectMapperModule(språk),
        )
}
