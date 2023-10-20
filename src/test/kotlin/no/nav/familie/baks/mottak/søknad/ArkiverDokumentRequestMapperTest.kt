package no.nav.familie.baks.mottak.søknad

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBVedlegg
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstotteVedlegg
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV4
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.v8.Søknad
import no.nav.familie.kontrakter.felles.dokarkiv.Dokumenttype
import no.nav.familie.kontrakter.felles.dokarkiv.v2.Filtype
import no.nav.familie.kontrakter.ks.søknad.v1.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ks.søknad.v1.Søknaddokumentasjon
import no.nav.familie.kontrakter.ks.søknad.v1.TekstPåSpråkMap
import no.nav.familie.kontrakter.ks.søknad.v4.KontantstøtteSøknad
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class ArkiverDokumentRequestMapperTest {
    @Test
    fun `toDto - skal opprette ArkiverDokumentRequest basert på KontantstøtteSøknad`() {
        val kontantstøtteSøknad: KontantstøtteSøknad = mockk()
        val dokumentasjon =
            Søknaddokumentasjon(
                Dokumentasjonsbehov.BEKREFTELESE_PÅ_BARNEHAGEPLASS,
                true,
                listOf(
                    no.nav.familie.kontrakter.ks.søknad.v1.Søknadsvedlegg(
                        "123",
                        "navn",
                        Dokumentasjonsbehov.BEKREFTELESE_PÅ_BARNEHAGEPLASS,
                    ),
                ),
                TekstPåSpråkMap(
                    mapOf(
                        "nb" to "Norge",
                        "nn" to "Noreg",
                        "en" to "Norway",
                    ),
                ),
            )
        every { kontantstøtteSøknad.dokumentasjon } returns
            listOf(
                dokumentasjon,
            )
        val dbKontantstøtteSøknad = DBKontantstøtteSøknad(søknadJson = "{}", fnr = "12345678910")
        val vedleggMap =
            mapOf(
                "123" to DBKontantstotteVedlegg(dokumentId = "123", søknadId = 0, data = ByteArray(0)),
            )
        val arkiverDokumentRequest =
            ArkiverDokumentRequestMapper.toDto(
                dbKontantstøtteSøknad,
                KontantstøtteSøknadV4(kontantstøtteSøknad = kontantstøtteSøknad),
                ByteArray(0),
                vedleggMap,
                ByteArray(0),
            )
        assertEquals("12345678910", arkiverDokumentRequest.fnr)
        assertEquals(false, arkiverDokumentRequest.forsøkFerdigstill)
        assertEquals(2, arkiverDokumentRequest.hoveddokumentvarianter.size)
        assertEquals(
            true,
            arkiverDokumentRequest.hoveddokumentvarianter.all {
                it.filtype in
                    listOf(
                        Filtype.PDFA,
                        Filtype.JSON,
                    ) && it.dokumenttype == Dokumenttype.KONTANTSTØTTE_SØKNAD
            },
        )
        assertEquals(1, arkiverDokumentRequest.vedleggsdokumenter.size)
        assertEquals(
            true,
            arkiverDokumentRequest.vedleggsdokumenter.all { it.filtype == Filtype.PDFA && it.dokumenttype == Dokumenttype.KONTANTSTØTTE_VEDLEGG },
        )
        assertEquals(
            1,
            arkiverDokumentRequest.vedleggsdokumenter.filter { it.tittel == "Bekreftelse på barnehageplass" }.size,
        )
    }

    @Test
    fun `toDto - skal opprette ArkiverDokumentRequest basert på BarnetrygdSøknad`() {
        val søknad: Søknad = mockk()
        val dokumentasjon =
            no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon(
                no.nav.familie.kontrakter.ba.søknad.v7.Dokumentasjonsbehov.AVTALE_DELT_BOSTED,
                true,
                listOf(
                    no.nav.familie.kontrakter.ba.søknad.v7.Søknadsvedlegg(
                        "123",
                        "navn",
                        no.nav.familie.kontrakter.ba.søknad.v7.Dokumentasjonsbehov.AVTALE_DELT_BOSTED,
                    ),
                ),
                TekstPåSpråkMap(
                    mapOf(
                        "nb" to "Norge",
                        "nn" to "Noreg",
                        "en" to "Norway",
                    ),
                ),
            )
        every { søknad.dokumentasjon } returns
            listOf(
                dokumentasjon,
            )
        every { søknad.søknadstype } returns Søknadstype.ORDINÆR

        val dbSøknad = DBSøknad(søknadJson = "{}", fnr = "12345678910")
        val vedleggMap =
            mapOf(
                "123" to DBVedlegg(dokumentId = "123", søknadId = 0, data = ByteArray(0)),
            )
        val arkiverDokumentRequest =
            ArkiverDokumentRequestMapper.toDto(
                dbSøknad,
                SøknadV8(søknad),
                ByteArray(0),
                vedleggMap,
                ByteArray(0),
            )
        assertEquals("12345678910", arkiverDokumentRequest.fnr)
        assertEquals(false, arkiverDokumentRequest.forsøkFerdigstill)
        assertEquals(2, arkiverDokumentRequest.hoveddokumentvarianter.size)
        assertEquals(
            true,
            arkiverDokumentRequest.hoveddokumentvarianter.all {
                it.filtype in
                    listOf(
                        Filtype.PDFA,
                        Filtype.JSON,
                    ) && it.dokumenttype == Dokumenttype.BARNETRYGD_ORDINÆR
            },
        )
        assertEquals(1, arkiverDokumentRequest.vedleggsdokumenter.size)
        assertEquals(
            true,
            arkiverDokumentRequest.vedleggsdokumenter.all { it.filtype == Filtype.PDFA && it.dokumenttype == Dokumenttype.BARNETRYGD_VEDLEGG },
        )
        assertEquals(
            1,
            arkiverDokumentRequest.vedleggsdokumenter.filter { it.tittel == "Avtale om delt bosted" }.size,
        )
    }
}
