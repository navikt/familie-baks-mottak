package no.nav.familie.ba.mottak.søknad

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.familie.ba.mottak.integrasjoner.PdfClient
import org.springframework.stereotype.Service
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.kontrakter.felles.objectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*


@Service
class PdfService(private val søknadRepository: SøknadRepository, private val pdfClient: PdfClient) {

    val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun lagPdf(id: String): ByteArray {
        log.info("Prøver å hente søknadspdf for $id")
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
