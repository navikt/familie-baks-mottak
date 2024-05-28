package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.VersjonertSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.VersjonertKontantstøtteSøknad

interface PdfService {
    fun lagBarnetrygdPdf(
        versjonertSøknad: VersjonertSøknad,
        dbBarnetrygdSøknad: DBBarnetrygdSøknad,
        språk: String = "nb",
    ): ByteArray

    fun lagKontantstøttePdf(
        versjonertSøknad: VersjonertKontantstøtteSøknad,
        dbKontantstøtteSøknad: DBKontantstøtteSøknad,
        språk: String = "nb",
    ): ByteArray
}
