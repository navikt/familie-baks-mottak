package no.nav.familie.ba.mottak.søknad

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.familie.ba.mottak.integrasjoner.PdfClient
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.ba.mottak.søknad.domene.SøknadSpråkvelgerService
import no.nav.familie.kontrakter.ba.Søknadstype as SøknadstypeGammel
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.felles.objectMapper
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*


@Service
class PdfService(
    private val pdfClient: PdfClient,
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService
) {
    fun lagPdf(dbSøknad: DBSøknad, språk: String = "nb"): ByteArray {

        if (dbSøknad.hentSøknadVersjon() == "v4") {
            val søknad = dbSøknad.hentSøknad()
            val path: String = when (søknad.søknadstype) {
                Søknadstype.UTVIDET -> "soknad-utvidet"
                else -> "soknad"
            }
            val søknadJson = søknadSpråkvelgerService.velgSøknadSpråk(søknad, språk)
            val feltMap = objectMapper.readValue(søknadJson, object : TypeReference<Map<String, Any>>() {})
            val utvidetFeltMap = feltMap + hentEkstraFelter(dbSøknad)

            return pdfClient.lagPdf(utvidetFeltMap, path)
        } else {
            val søknad = dbSøknad.hentSøknadV3()
            val path: String = when (søknad.søknadstype) {
                SøknadstypeGammel.UTVIDET -> "soknad-utvidet"
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
                "v4" -> dbSøknad.hentSøknad().søker.navn.verdi.getValue("nb")
                else -> dbSøknad.hentSøknadV3().søker.navn.verdi
            },
            "fodselsnummer" to dbSøknad.fnr,
            "label" to when(dbSøknad.hentSøknadVersjon()) {
                "v4" -> when(dbSøknad.hentSøknad().søknadstype) {
                    Søknadstype.UTVIDET -> "Søknad om utvidet barnetrygd"
                    else -> "Søknad om ordinær barnetrygd"
                }
                else -> when(dbSøknad.hentSøknadV3().søknadstype) {
                    SøknadstypeGammel.UTVIDET -> "Søknad om utvidet barnetrygd"
                    else -> "Søknad om ordinær barnetrygd"
                }
            }
        )
    }

}
