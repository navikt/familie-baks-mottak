package no.nav.familie.ba.mottak.søknad

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.familie.ba.mottak.integrasjoner.PdfClient
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.kontrakter.ba.Søknadstype
import no.nav.familie.kontrakter.felles.objectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*


@Service
class PdfService(private val søknadRepository: SøknadRepository, private val pdfClient: PdfClient) {

    val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun lagPdf(id: String): ByteArray {
        log.info("Prøver å hente søknadspdf for $id")
        val dbSøknad: DBSøknad = søknadRepository.hentDBSøknad(id.toLong()) ?: error("Kunne ikke finne søknad ($id) i database")

        if (dbSøknad.hentSøknadVersjon() == "v2") {
            val søknad = dbSøknad.hentSøknad()
            val path: String = when (søknad.søknadstype) {
                Søknadstype.UTVIDET -> "soknad-utvidet"
                else -> "soknad"
            }
            val feltMap = objectMapper.convertValue(søknad, object : TypeReference<Map<String, Any>>() {})
            val utvidetFeltMap = feltMap + hentEkstraFelter(dbSøknad)
            return pdfClient.lagPdf(utvidetFeltMap, path)

        } else {
            val søknad = dbSøknad.hentSøknadV3()
            val path: String = when (søknad.søknadstype) {
                Søknadstype.UTVIDET -> "soknad-utvidet"
                else -> "soknad"
            }
            val feltMap = objectMapper.convertValue(søknad, object : TypeReference<Map<String, Any>>() {})
            val utvidetFeltMap = feltMap + hentEkstraFelter(dbSøknad)
            return pdfClient.lagPdf(utvidetFeltMap, path)
        }

    }

    private fun hentEkstraFelter(dbSøknad: DBSøknad): Map<String, String> {
        return mapOf(
            "dokumentDato" to dbSøknad.opprettetTid.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).localizedBy(Locale("no"))
            ),
            "navn" to when(dbSøknad.hentSøknadVersjon()) {
                "v2" -> dbSøknad.hentSøknad().søker.navn.verdi
                else -> dbSøknad.hentSøknadV3().søker.navn.verdi
            },
            "fodselsnummer" to dbSøknad.fnr,
            "label" to when(dbSøknad.hentSøknadVersjon()) {
                "v2" -> when(dbSøknad.hentSøknad().søknadstype) {
                    Søknadstype.UTVIDET -> "Søknad om utvidet barnetrygd"
                    else -> "Søknad om ordinær barnetrygd"
                }
                else -> when(dbSøknad.hentSøknadV3().søknadstype) {
                    Søknadstype.UTVIDET -> "Søknad om utvidet barnetrygd"
                    else -> "Søknad om ordinær barnetrygd"
                }
            }
        )
    }

}
