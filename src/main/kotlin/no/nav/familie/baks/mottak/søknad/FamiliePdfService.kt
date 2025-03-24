package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.domene.FeltMap
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
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

    private fun lagBarnetrygdFeltMap(søknad: DBBarnetrygdSøknad): FeltMap = FeltMap("", emptyList())

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
