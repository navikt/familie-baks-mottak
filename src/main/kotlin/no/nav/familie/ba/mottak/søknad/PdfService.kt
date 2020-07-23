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
        val utvidetFeltMap = (feltMap - "verdiliste") + hentEkstraFelter(dbSøknad) + verdilisteUtenSøker(feltMap)
        return pdfClient.lagPdf(utvidetFeltMap)
    }

    private fun hentEkstraFelter(dbSøknad: DBSøknad): Map<String, String> {
        return mapOf(
                "dokumentDato" to dbSøknad.opprettetTid.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).localizedBy(Locale("no"))),
                "navn" to dbSøknad.hentSøknad().søker.verdi.navn.verdi,
                "fodselsnummer" to dbSøknad.fnr
        )
    }

    private fun verdilisteUtenSøker(feltMap: Map<String, Any>): Map<String, List<Map<String, Any>>> {
        return mapOf("verdiliste" to (feltMap["verdiliste"] as List<Map<String, Any>>).filter{it["label"] != "Søker"})
    }

}
