package no.nav.familie.ba.mottak.søknad

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.familie.ba.mottak.integrasjoner.PdfClient
import org.springframework.stereotype.Service
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.kontrakter.felles.objectMapper
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*


@Service
class PdfService(private val søknadRepository: SøknadRepository, private val pdfClient: PdfClient) {

    fun lagPdf(id: String): ByteArray {
        val dbSøknad = søknadRepository.hentDBSøknad(id.toLong()) ?: error("Kunne ikke finne søknad ($id) i database")
        val feltMap = objectMapper.convertValue(dbSøknad.hentSøknad(), object:TypeReference<Map<String,Any>>() {})
        val utvidetFeltMap = feltMap + hentEkstraFelter(dbSøknad)
        return pdfClient.lagPdf(utvidetFeltMap)
    }

    private fun hentEkstraFelter(dbSøknad: DBSøknad): Map<String, String> {
        return mapOf(
                "dokumentDato" to dbSøknad.opprettetTid.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).localizedBy(Locale("no"))
                ),
                "navn" to dbSøknad.hentSøknad().søker.navn.verdi,
                "fodselsnummer" to dbSøknad.fnr
        )
    }

}
