package no.nav.familie.ba.mottak.søknad

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.familie.ba.mottak.integrasjoner.PdfClient
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.ba.mottak.søknad.domene.SøknadSpråkvelgerService
import no.nav.familie.ba.mottak.søknad.domene.SøknadV7
import no.nav.familie.ba.mottak.søknad.domene.SøknadV8
import no.nav.familie.ba.mottak.søknad.domene.VersjonertSøknad
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsfelt
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.felles.objectMapper
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Service
class PdfService(
    private val pdfClient: PdfClient,
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService
) {

    fun lagPdf(versjonertSøknad: VersjonertSøknad, dbSøknad: DBSøknad, språk: String = "nb"): ByteArray {

        val søknadJson = søknadSpråkvelgerService.velgSøknadSpråk(versjonertSøknad, språk)

        val (søknadstype, navn) = when (versjonertSøknad) {
            is SøknadV7 -> {
                Pair(versjonertSøknad.søknad.søknadstype, versjonertSøknad.søknad.søker.navn)
            }
            is SøknadV8 -> {
                Pair(versjonertSøknad.søknad.søknadstype, versjonertSøknad.søknad.søker.navn)
            }
        }

        val path: String = søknadstypeTilPath(søknadstype)
        val feltMap = objectMapper.readValue(søknadJson, object : TypeReference<Map<String, Any>>() {})
        val utvidetFeltMap = feltMap + hentEkstraFelter(
            dbSøknad = dbSøknad,
            navn = navn,
            søknadstype = søknadstype
        )
        return pdfClient.lagPdf(utvidetFeltMap, path)
    }

    private fun søknadstypeTilPath(søknadstype: Søknadstype): String {
        return when (søknadstype) {
            Søknadstype.UTVIDET -> "soknad-utvidet"
            else -> "soknad"
        }
    }

    private fun hentEkstraFelter(
        dbSøknad: DBSøknad,
        navn: Søknadsfelt<String>,
        søknadstype: Søknadstype
    ): Map<String, String> {
        return mapOf(
            "dokumentDato" to dbSøknad.opprettetTid.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).localizedBy(Locale("no"))
            ),
            "navn" to navn.verdi.getValue("nb"),
            "fodselsnummer" to dbSøknad.fnr,
            "label" to when (søknadstype) {
                Søknadstype.UTVIDET -> "Søknad om utvidet barnetrygd"
                else -> "Søknad om ordinær barnetrygd"
            }
        )
    }
}
