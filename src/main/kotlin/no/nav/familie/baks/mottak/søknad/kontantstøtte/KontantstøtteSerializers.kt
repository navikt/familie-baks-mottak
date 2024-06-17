package no.nav.familie.baks.mottak.søknad.kontantstøtte

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import no.nav.familie.kontrakter.ks.søknad.v1.Søknadsfelt
import no.nav.familie.kontrakter.ks.søknad.v1.TekstPåSpråkMap

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
) : JsonSerializer<Søknadsfelt<*>>() {
    override fun serialize(
        søknadsFelt: Søknadsfelt<*>,
        jsonGenerator: JsonGenerator,
        serializerProvider: SerializerProvider,
    ) = jsonGenerator.writeObject(
        mapOf(
            "label" to søknadsFelt.label[språk],
            "verdi" to søknadsFelt.verdi[språk],
        ),
    )
}

class TekstPåSpråkMapSerializer(
    private val språk: String,
) : JsonSerializer<TekstPåSpråkMap>() {
    override fun serialize(
        tekstPåSpråkMap: TekstPåSpråkMap,
        jsonGenerator: JsonGenerator,
        serializerProvider: SerializerProvider,
    ) = jsonGenerator.writeObject(
        tekstPåSpråkMap[språk],
    )
}
