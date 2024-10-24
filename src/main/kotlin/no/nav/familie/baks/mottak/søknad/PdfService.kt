package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.integrasjoner.PdfClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV9
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.VersjonertBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.ks.søknad.StøttetVersjonertKontantstøtteSøknad
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV4
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV5
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Service
class PdfService(
    private val familieDokumentPdfClient: PdfClient,
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService,
) {
    fun lagBarnetrygdPdf(
        versjonertBarnetrygdSøknad: VersjonertBarnetrygdSøknad,
        dbBarnetrygdSøknad: DBBarnetrygdSøknad,
        språk: String,
    ): ByteArray {
        val barnetrygdSøknadMapForSpråk =
            søknadSpråkvelgerService.konverterBarnetrygdSøknadTilMapForSpråk(versjonertBarnetrygdSøknad, språk)

        val (søknadstype, navn) =
            when (versjonertBarnetrygdSøknad) {
                is BarnetrygdSøknadV8 ->
                    Pair(versjonertBarnetrygdSøknad.barnetrygdSøknad.søknadstype, versjonertBarnetrygdSøknad.barnetrygdSøknad.søker.navn)

                is BarnetrygdSøknadV9 ->
                    Pair(versjonertBarnetrygdSøknad.barnetrygdSøknad.søknadstype, versjonertBarnetrygdSøknad.barnetrygdSøknad.søker.navn)
            }

        val path: String = søknadstypeTilPath(søknadstype)
        val ekstraFelterMap =
            hentEkstraFelter(
                navn = navn.verdi.getValue("nb"),
                opprettetTid = dbBarnetrygdSøknad.opprettetTid,
                fnr = dbBarnetrygdSøknad.fnr,
                label =
                when (søknadstype) {
                    Søknadstype.UTVIDET -> "Søknad om utvidet barnetrygd"
                    else -> "Søknad om ordinær barnetrygd"
                },
            )
        return familieDokumentPdfClient.lagPdf(path, barnetrygdSøknadMapForSpråk + ekstraFelterMap)
    }

    fun lagKontantstøttePdf(
        versjonertSøknad: StøttetVersjonertKontantstøtteSøknad,
        dbKontantstøtteSøknad: DBKontantstøtteSøknad,
        språk: String,
    ): ByteArray {
        val kontantstøtteSøknadMapForSpråk =
            søknadSpråkvelgerService.konverterKontantstøtteSøknadTilMapForSpråk(versjonertSøknad, språk)

        val navn =
            when (versjonertSøknad) {
                is VersjonertKontantstøtteSøknadV4 -> versjonertSøknad.kontantstøtteSøknad.søker.navn
                is VersjonertKontantstøtteSøknadV5 -> versjonertSøknad.kontantstøtteSøknad.søker.navn
            }

        val ekstraFelterMap =
            hentEkstraFelter(
                navn = navn.verdi.getValue("nb"),
                opprettetTid = dbKontantstøtteSøknad.opprettetTid,
                fnr = dbKontantstøtteSøknad.fnr,
                label = "Søknad om kontantstøtte",
            )
        return familieDokumentPdfClient.lagPdf("kontantstotte-soknad", kontantstøtteSøknadMapForSpråk + ekstraFelterMap)
    }

    private fun søknadstypeTilPath(søknadstype: Søknadstype): String =
        when (søknadstype) {
            Søknadstype.UTVIDET -> "soknad-utvidet"
            else -> "soknad"
        }

    private fun hentEkstraFelter(
        opprettetTid: LocalDateTime,
        navn: String,
        fnr: String,
        label: String,
    ): Map<String, String> =
        mapOf(
            "dokumentDato" to
                    opprettetTid.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).localizedBy(Locale.of("no")),
                    ),
            "navn" to navn,
            "fodselsnummer" to fnr,
            "label" to label,
            "maalform" to "NB",
        )

    companion object {
        val logger = LoggerFactory.getLogger(this::class.java)
    }
}
