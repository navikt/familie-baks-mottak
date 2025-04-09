@file:Suppress("ktlint:standard:filename")

package no.nav.familie.baks.mottak.søknad.barnetrygd.mapper

import no.nav.familie.baks.mottak.domene.LabelVerdiPar
import no.nav.familie.kontrakter.ba.søknad.v8.Barn

data class HvilkeBarnSeksjon(
    val navn: LabelVerdiPar<String>,
    val alder: LabelVerdiPar<String>,
    val ident: LabelVerdiPar<String>,
    val registrertBostedType: LabelVerdiPar<String>? = null,
)

fun mapTilHvilkeBarnSeksjon(
    barna: List<Barn>,
    teksterUtenomSpørsmål: Map<String, Map<String, String>>,
    språk: String,
): LabelVerdiPar<List<HvilkeBarnSeksjon>> =
    LabelVerdiPar(
        label = hentTekst(teksterUtenomSpørsmål, "pdf.hvilkebarn.seksjonstittel", språk),
        verdi =
            barna.map {
                HvilkeBarnSeksjon(
                    navn = oversettOgFormaterTilUtskriftsformat(it.navn, språk),
                    alder = oversettOgFormaterTilUtskriftsformat(it.alder, språk),
                    ident = oversettOgFormaterTilUtskriftsformat(it.ident, språk),
                    registrertBostedType =
                        it.registrertBostedType.let { registrertBostedType ->
                            oversettOgFormaterTilUtskriftsformat(
                                registrertBostedType,
                                språk,
                            )
                        },
                )
            },
    )
