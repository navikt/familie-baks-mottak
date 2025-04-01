package no.nav.familie.baks.mottak.søknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.familie.baks.mottak.domene.FeltMap
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsfelt
import no.nav.familie.kontrakter.ba.søknad.v9.BarnetrygdSøknad
import no.nav.familie.kontrakter.felles.objectMapper
import org.springframework.stereotype.Service

@Service
class FamiliePdfService(
    private val familiePdfClient: FamiliePdfClient,
) {
    fun lagBarnetrygdPdfKvittering(
        søknad: DBBarnetrygdSøknad,
        språk: String,
    ): ByteArray {
        // TODO mangler vedleggstitler?
        val feltmap = lagBarnetrygdFeltMap(søknad)

        return familiePdfClient.opprettPdf(feltmap)
    }

    private fun lagBarnetrygdFeltMap(søknad: DBBarnetrygdSøknad): FeltMap {
        val test = objectMapper.readValue<BarnetrygdSøknad>(søknad.søknadJson)

//        println(mapTilBarnetrygd(test))

        // Placeholderkode v
        val feltmap = FeltMap("", emptyList())
        return feltmap
    }

    fun mapTilBarnetrygd(søknad: BarnetrygdSøknad): StrukturertBarnetrygdSøknad = StrukturertBarnetrygdSøknad(søker = mapTilSøkerSeksjon(søknad))

    fun mapTilSøkerSeksjon(søknad: BarnetrygdSøknad): SøkerSeksjon = SøkerSeksjon(navn = søknad.spørsmål["erAsylsøker"])

    data class StrukturertBarnetrygdSøknad(
        val søker: SøkerSeksjon,
        val omDeg: String? = null,
        val dinLivssituasjon: String? = null,
        val hvilketBarn: String? = null,
        val omBarna: String? = null,
        val omBarnet: String? = null,
        val eøsSteg: String? = null,
        val enkeltbarnEøsSteg: String? = null,
        val vedlegg: String? = null,
    )

    data class SøkerSeksjon(
        val navn: Søknadsfelt<Any>? = null,
    )

    fun lagKontantstøttePdfKvittering(
        søknad: DBKontantstøtteSøknad,
        språk: String,
    ): ByteArray {
        // TODO mangler vedleggstitler?
        val feltmap = lagKontantstøtteFeltMap(søknad)

        return familiePdfClient.opprettPdf(feltmap)
    }

    private fun lagKontantstøtteFeltMap(søknad: DBKontantstøtteSøknad): FeltMap = FeltMap("", emptyList())
}
