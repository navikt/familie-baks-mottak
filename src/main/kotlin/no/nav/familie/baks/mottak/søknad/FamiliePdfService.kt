package no.nav.familie.baks.mottak.søknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.familie.baks.mottak.domene.FeltMap
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.kontrakter.ba.søknad.StøttetVersjonertBarnetrygdSøknad
import no.nav.familie.kontrakter.ba.søknad.v9.BarnetrygdSøknad
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.søknad.Søknadsfelt
import org.springframework.stereotype.Service

@Service
class FamiliePdfService(
    private val familiePdfClient: FamiliePdfClient,
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService,
) {
    fun lagBarnetrygdPdfKvittering(
        versjonertBarnetrygdSøknad: StøttetVersjonertBarnetrygdSøknad,
        søknad: DBBarnetrygdSøknad,
        språk: String,
    ): ByteArray {
        // TODO mangler vedleggstitler?
        val feltmap = lagBarnetrygdFeltMap(versjonertBarnetrygdSøknad, søknad)

        return familiePdfClient.opprettPdf(feltmap)
    }

    private fun lagBarnetrygdFeltMap(
        versjonertBarnetrygdSøknad: StøttetVersjonertBarnetrygdSøknad,
        søknad: DBBarnetrygdSøknad,
    ): FeltMap {
        val barnetrygdSøknad = objectMapper.readValue<BarnetrygdSøknad>(søknad.søknadJson)

        val ææ = mapTilBarnetrygd(barnetrygdSøknad)

        println(søknadSpråkvelgerService.konverterBarnetrygdSøknadTilMapForSpråk(versjonertBarnetrygdSøknad, "nb"))
//        println(ææ)

        // Placeholderkode v
        val feltmap = FeltMap("", emptyList())
        return feltmap
    }

    fun mapTilBarnetrygd(søknad: BarnetrygdSøknad): StrukturertBarnetrygdSøknad = StrukturertBarnetrygdSøknad(søker = mapTilSøkerSeksjon(søknad))

    fun mapTilSøkerSeksjon(søknad: BarnetrygdSøknad): SøkerSeksjon = SøkerSeksjon(navn = søknad.søker.spørsmål["erAsylsøker"] as Søknadsfelt<String>)

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
        val navn: Søknadsfelt<String>? = null,
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
