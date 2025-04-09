@file:Suppress("ktlint:standard:filename")

package no.nav.familie.baks.mottak.søknad.barnetrygd.mapper

import no.nav.familie.baks.mottak.domene.LabelVerdiPar
import no.nav.familie.kontrakter.ba.søknad.v8.Søker

data class OmDegSeksjon(
    val ident: LabelVerdiPar<String>,
    val statsborgerskap: LabelVerdiPar<String>,
    val sivilstatus: LabelVerdiPar<String>,
    val adresse: LabelVerdiPar<String>,
    val borPåRegistrertAdresse: LabelVerdiPar<String>,
    val værtINorgeITolvMåneder: LabelVerdiPar<String>,
)

fun mapTilOmDegSeksjon(
    søker: Søker,
    teksterUtenomSpørsmål: Map<String, Map<String, String>>,
    språk: String,
): LabelVerdiPar<OmDegSeksjon> =
    LabelVerdiPar(
        label = hentTekst(teksterUtenomSpørsmål, "pdf.omdeg.seksjonstittel", språk),
        verdi =
            OmDegSeksjon(
                ident = oversettOgFormaterTilUtskriftsformat(søker.ident, språk),
                statsborgerskap = oversettOgFormaterTilUtskriftsformat(søker.statsborgerskap, språk),
                sivilstatus = oversettOgFormaterTilUtskriftsformat(søker.sivilstand, språk),
                adresse = oversettOgFormaterTilUtskriftsformat(søker.adresse, språk),
                borPåRegistrertAdresse = hentSpørsmålOversettOgFormatertilUtskrift<String>(søker.spørsmål, "borPåRegistrertAdresse", språk),
                værtINorgeITolvMåneder = hentSpørsmålOversettOgFormatertilUtskrift<String>(søker.spørsmål, "værtINorgeITolvMåneder", språk),
            ),
    )
