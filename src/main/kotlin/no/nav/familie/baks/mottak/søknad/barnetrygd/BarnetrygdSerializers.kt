package no.nav.familie.baks.mottak.søknad.barnetrygd

import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsfelt
import no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.module.SimpleModule

class BarnetrygdSøknadObjectMapperModule(
    språk: String,
) : SimpleModule() {
    init {
        addSerializer(Søknadsfelt::class.java, SøknadsfeltSerializer(språk))
        addSerializer(Søknaddokumentasjon::class.java, SøknaddokumentasjonSerializer(språk))
    }
}

class SøknaddokumentasjonSerializer(
    private val språk: String,
) : ValueSerializer<Søknaddokumentasjon>() {
    override fun serialize(
        dokumentasjon: Søknaddokumentasjon,
        jsonGenerator: JsonGenerator,
        serializerProvider: SerializationContext,
    ) {
        jsonGenerator.writePOJO(
            mapOf(
                "dokumentasjonsbehov" to dokumentasjon.dokumentasjonsbehov,
                "harSendtInn" to dokumentasjon.harSendtInn,
                "opplastedeVedlegg" to dokumentasjon.opplastedeVedlegg,
                "dokumentasjonSpråkTittel" to dokumentasjon.dokumentasjonSpråkTittel[språk],
            ),
        )
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
        if (søknadsFelt.label[språk] == "teksterTilPdf") {
            println(søknadsFelt)
        }
        jsonGenerator.writePOJO(
            mapOf(
                "label" to søknadsFelt.label[språk],
                "verdi" to søknadsFelt.verdi[språk],
            ),
        )
    }
}
