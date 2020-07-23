package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.integrasjoner.PdfClient
import org.springframework.stereotype.Service
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*


@Service
class PdfService(private val søknadRepository: SøknadRepository, private val pdfClient: PdfClient) {

    fun lagPdf(id: String): ByteArray {
        val dbSøknad = søknadRepository.hentDBSøknad(id.toLong()) ?: error("Kunne ikke finne søknad ($id) i database")
        val feltMap = SøknadTreeWalker.mapSøknadsfelter(dbSøknad.hentSøknad())
        val utvidetFeltMap = feltMap + hentEkstraFelter(dbSøknad) - "Søker"
        val søknadPdf = pdfClient.lagPdf(utvidetFeltMap)
        // TODO: fjern out, return pdfClient.lagPdf()
        val out = FileOutputStream("out.pdf")
        out.write(søknadPdf)
        out.close()
        return søknadPdf
    }

    private fun hentEkstraFelter(dbSøknad: DBSøknad): Map<String, String> {
        return mapOf(
                "dokumentDato" to dbSøknad.opprettetTid.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).localizedBy(Locale("no"))),
                "navn" to dbSøknad.hentSøknad().søker.verdi.navn.verdi,
                "fodselsnummer" to dbSøknad.fnr
        )
    }

}
