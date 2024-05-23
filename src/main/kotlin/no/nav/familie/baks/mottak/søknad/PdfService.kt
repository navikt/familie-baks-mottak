package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.dokgen.DokGen
import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV7
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.VersjonertSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV3
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV4
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.VersjonertKontantstøtteSøknad
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Service
class PdfService(
    private val familieDokumentClient: FamilieDokumentClient,
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService,
    private val dokgen: DokGen,
) {
    fun lagBarnetrygdPdf(
        versjonertSøknad: VersjonertSøknad,
        dbSøknad: DBSøknad,
        språk: String = "nb",
    ): ByteArray {
        val barnetrygdSøknadMapForSpråk =
            søknadSpråkvelgerService.konverterBarnetrygdSøknadTilMapForSpråk(versjonertSøknad, språk)

        val (søknadstype, navn) =
            when (versjonertSøknad) {
                is SøknadV7 -> {
                    Pair(versjonertSøknad.søknad.søknadstype, versjonertSøknad.søknad.søker.navn)
                }

                is SøknadV8 -> {
                    Pair(versjonertSøknad.søknad.søknadstype, versjonertSøknad.søknad.søker.navn)
                }
            }

        val path: String = søknadstypeTilPath(søknadstype)
        val ekstraFelterMap =
            hentEkstraFelter(
                navn = navn.verdi.getValue("nb"),
                opprettetTid = dbSøknad.opprettetTid,
                fnr = dbSøknad.fnr,
                label =
                    when (søknadstype) {
                        Søknadstype.UTVIDET -> "Søknad om utvidet barnetrygd"
                        else -> "Søknad om ordinær barnetrygd"
                    },
            )

        return familieDokumentClient.lagPdf(
            html = dokgen.lagHtmlTilPdf(path, barnetrygdSøknadMapForSpråk + ekstraFelterMap),
        )
    }

    fun lagKontantstøttePdf(
        versjonertSøknad: VersjonertKontantstøtteSøknad,
        dbKontantstøtteSøknad: DBKontantstøtteSøknad,
        språk: String = "nb",
    ): ByteArray {
        val kontantstøtteSøknadMapForSpråk =
            søknadSpråkvelgerService.konverterKontantstøtteSøknadTilMapForSpråk(versjonertSøknad, språk)

        val navn =
            when (versjonertSøknad) {
                is KontantstøtteSøknadV3 -> versjonertSøknad.kontantstøtteSøknad.søker.navn
                is KontantstøtteSøknadV4 -> versjonertSøknad.kontantstøtteSøknad.søker.navn
            }

        val ekstraFelterMap =
            hentEkstraFelter(
                navn = navn.verdi.getValue("nb"),
                opprettetTid = dbKontantstøtteSøknad.opprettetTid,
                fnr = dbKontantstøtteSøknad.fnr,
                label = "Søknad om kontantstøtte",
            )
        return familieDokumentClient.lagPdf(
            html = dokgen.lagHtmlTilPdf("kontantstotte-soknad", kontantstøtteSøknadMapForSpråk + ekstraFelterMap),
        )
    }

    private fun søknadstypeTilPath(søknadstype: Søknadstype): String {
        return when (søknadstype) {
            Søknadstype.UTVIDET -> "soknad-utvidet"
            else -> "soknad"
        }
    }

    private fun hentEkstraFelter(
        opprettetTid: LocalDateTime,
        navn: String,
        fnr: String,
        label: String,
    ): Map<String, String> {
        return mapOf(
            "dokumentDato" to
                opprettetTid.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).localizedBy(Locale.of("no")),
                ),
            "navn" to navn,
            "fodselsnummer" to fnr,
            "label" to label,
            "maalform" to "NB",
        )
    }

    companion object {
        val logger = LoggerFactory.getLogger(this::class.java)
    }
}

@Configuration
class DokgenConfig {
    @Bean
    fun dokgen() = DokGen()
}
