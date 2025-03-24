package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.domene.FeltMap
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import org.springframework.stereotype.Service

@Service
class FamiliePdfService(
    private val familiePdfClient: FamiliePdfClient,
    private val søknadRepository: SøknadRepository,
) {
    fun lagPdfKvittering(
        søknad: DBBarnetrygdSøknad,
        språk: String,
    ): ByteArray {
        // TODO mangler vedleggstitler?
        val feltmap = lagFeltMap(søknad)

        return familiePdfClient.opprettPdf(feltmap)
    }

    private fun lagFeltMap(søknad: DBBarnetrygdSøknad): FeltMap = FeltMap("", emptyList())
}
