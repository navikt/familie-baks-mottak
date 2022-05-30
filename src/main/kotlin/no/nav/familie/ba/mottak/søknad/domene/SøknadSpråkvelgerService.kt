package no.nav.familie.ba.mottak.søknad.domene

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.convertValue
import no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsfelt
import org.springframework.stereotype.Service
import no.nav.familie.kontrakter.felles.objectMapper as getObjectMapper

@Service
class SøknadSpråkvelgerService {
    private val defaultLocale = "nb"
    var valgtLocale = defaultLocale
    val objectMapper = getObjectMapper.apply {
        registerModule(
            SimpleModule().apply {
                addSerializer(Søknadsfelt::class.java, SøknadsfeltSerializer())
                addSerializer(Søknaddokumentasjon::class.java, SøknaddokumentasjonSerializer())
            }
        )
    }

    fun velgSøknadSpråk(versjonertSøknad: VersjonertSøknad, språk: String): String {

        valgtLocale = språk

        val asMap = objectMapper.convertValue<MutableMap<String, Any>>(
            when (versjonertSøknad) {
                is SøknadV7 -> versjonertSøknad.søknad
            }
        )
        asMap["teksterUtenomSpørsmål"] = when (versjonertSøknad) {
            is SøknadV7 -> versjonertSøknad.søknad.teksterUtenomSpørsmål
        }.mapValues { it.value[valgtLocale] }
        valgtLocale = defaultLocale
        return objectMapper.writeValueAsString(asMap)
    }

    inner class SøknaddokumentasjonSerializer : JsonSerializer<Søknaddokumentasjon>() {
        override fun serialize(dokumentasjon: Søknaddokumentasjon, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider) {
            return jsonGenerator.writeObject(
                mapOf(
                    "dokumentasjonsbehov" to dokumentasjon.dokumentasjonsbehov,
                    "harSendtInn" to dokumentasjon.harSendtInn,
                    "opplastedeVedlegg" to dokumentasjon.opplastedeVedlegg,
                    "dokumentasjonSpråkTittel" to dokumentasjon.dokumentasjonSpråkTittel[valgtLocale]
                )
            )
        }
    }

    inner class SøknadsfeltSerializer : JsonSerializer<Søknadsfelt<*>>() {
        override fun serialize(søknadsFelt: Søknadsfelt<*>, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider) {
            return jsonGenerator.writeObject(
                mapOf(
                    "label" to søknadsFelt.label[valgtLocale],
                    "verdi" to søknadsFelt.verdi[valgtLocale]
                )
            )
        }
    }
}
