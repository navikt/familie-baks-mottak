package no.nav.familie.baks.mottak.søknad.kontantstøtte

import no.nav.familie.kontrakter.felles.søknad.Søknadsfelt
import no.nav.familie.kontrakter.ks.søknad.v1.TekstPåSpråkMap
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.module.SimpleModule

class KontantstøtteObjectMapperModule(
    språk: String,
) : SimpleModule() {
    init {
        addSerializer(Søknadsfelt::class.java, SøknadsfeltSerializer(språk))
        addSerializer(TekstPåSpråkMap::class.java, TekstPåSpråkMapSerializer(språk))
    }
}

class SøknadsfeltSerializer(
    private val språk: String,
) : ValueSerializer<Søknadsfelt<*>>() {
    override fun serialize(
        søknadsFelt: Søknadsfelt<*>,
        jsonGenerator: JsonGenerator,
        serializerProvider: SerializationContext,
    ) {
        jsonGenerator.writePOJO(
            mapOf(
                "label" to søknadsFelt.label[språk],
                "verdi" to søknadsFelt.verdi[språk],
            ),
        )
    }
}

class TekstPåSpråkMapSerializer(
    private val språk: String,
) : ValueSerializer<TekstPåSpråkMap>() {
    override fun serialize(
        tekstPåSpråkMap: TekstPåSpråkMap,
        jsonGenerator: JsonGenerator,
        serializerProvider: SerializationContext,
    ) {
        jsonGenerator.writePOJO(
            tekstPåSpråkMap[språk],
        )
    }
}
