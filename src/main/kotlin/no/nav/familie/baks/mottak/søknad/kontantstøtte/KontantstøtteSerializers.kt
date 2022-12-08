package no.nav.familie.baks.mottak.søknad.kontantstøtte

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import no.nav.familie.kontrakter.ks.søknad.v1.Søknaddokumentasjon
import no.nav.familie.kontrakter.ks.søknad.v1.Søknadsfelt

class KontantstøtteObjectMapperModule(språk: String) : SimpleModule() {

    init {
        addSerializer(Søknaddokumentasjon::class.java, SøknaddokumentasjonSerializer(språk))
        addSerializer(Søknadsfelt::class.java, SøknadsfeltSerializer(språk))
    }
}

class SøknaddokumentasjonSerializer(private val språk: String) : JsonSerializer<Søknaddokumentasjon>() {
    override fun serialize(dokumentasjon: Søknaddokumentasjon, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider) {
        return jsonGenerator.writeObject(
            mapOf(
                "dokumentasjonsbehov" to dokumentasjon.dokumentasjonsbehov,
                "harSendtInn" to dokumentasjon.harSendtInn,
                "opplastedeVedlegg" to dokumentasjon.opplastedeVedlegg,
                "dokumentasjonSpråkTittel" to dokumentasjon.dokumentasjonSpråkTittel[språk]
            )
        )
    }
}

class SøknadsfeltSerializer(private val språk: String) : JsonSerializer<Søknadsfelt<*>>() {
    override fun serialize(søknadsFelt: Søknadsfelt<*>, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider) {
        return jsonGenerator.writeObject(
            mapOf(
                "label" to søknadsFelt.label[språk],
                "verdi" to søknadsFelt.verdi[språk]
            )
        )
    }
}
