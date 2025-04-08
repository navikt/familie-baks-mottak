package no.nav.familie.baks.mottak.søknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.familie.baks.mottak.domene.FeltMap
import no.nav.familie.baks.mottak.domene.PdfConfig
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.mapper.mapTilBarnetrygd
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.kontrakter.ba.søknad.v9.BarnetrygdSøknad
import no.nav.familie.kontrakter.felles.objectMapper
import org.springframework.stereotype.Service

@Service
class FamiliePdfService(
    private val familiePdfClient: FamiliePdfClient,
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService,
) {
    fun lagBarnetrygdPdfKvittering(
        søknad: DBBarnetrygdSøknad,
        språk: String,
    ): ByteArray {
        // TODO mangler vedleggstitler?

        val feltmap = lagBarnetrygdFeltMap(søknad, språk)

        return familiePdfClient.opprettPdf(feltmap)
    }

    private fun lagBarnetrygdFeltMap(
        søknad: DBBarnetrygdSøknad,
        språk: String,
    ): FeltMap {
        val barnetrygdSøknad = objectMapper.readValue<BarnetrygdSøknad>(søknad.søknadJson)
        val barnetrygdSøknadRiktigSpråk = mapTilBarnetrygd(barnetrygdSøknad, språk)
        val verdiliste = finnFelter(barnetrygdSøknadRiktigSpråk) // Husk at dette er en egen seksjon så d er litt fucked
        val feltmap = FeltMap(label = "Søknad om barnetrygd", verdiliste = verdiliste, pdfConfig = PdfConfig(true, språk))
        println("feltmap: " + feltmap)
        return feltmap
    }

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
