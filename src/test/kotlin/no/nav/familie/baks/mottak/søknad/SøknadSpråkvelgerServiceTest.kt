package no.nav.familie.baks.mottak.søknad

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.søknad.Søknadsfelt
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV6
import no.nav.familie.kontrakter.ks.søknad.v1.Søknaddokumentasjon
import no.nav.familie.kontrakter.ks.søknad.v1.TekstPåSpråkMap
import no.nav.familie.kontrakter.ks.søknad.v6.Barn
import no.nav.familie.kontrakter.ks.søknad.v6.KontantstøtteSøknad
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class SøknadSpråkvelgerServiceTest {
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService = SøknadSpråkvelgerService()

    @Test
    fun `konverterKontantstøtteSøknadTilMapForSpråk - skal konvertere KontantstøtteSøknad til et Map med tekster på spesifisert språk`() {
        val kontantstøtteSøknad: KontantstøtteSøknad = mockk(relaxed = true)
        val barn: Barn = mockk(relaxed = true)
        val dokumentasjon: Søknaddokumentasjon = mockk(relaxed = true)
        val tekstPåSpråk =
            mapOf(
                "nb" to "Norge",
                "nn" to "Noreg",
                "en" to "Norway",
            )
        val teksterTilPdf =
            mapOf(
                "testApiNavn" to
                    TekstPåSpråkMap(
                        tekstPåSpråk,
                    ),
            )
        every { barn.teksterTilPdf } returns teksterTilPdf
        every { kontantstøtteSøknad.barn } returns
            listOf(
                barn,
            )
        every { kontantstøtteSøknad.teksterTilPdf } returns teksterTilPdf
        every { kontantstøtteSøknad.erBarnAdoptert } returns
            Søknadsfelt(
                label = tekstPåSpråk,
                verdi = tekstPåSpråk,
            )
        every { dokumentasjon.dokumentasjonSpråkTittel } returns TekstPåSpråkMap(tekstPåSpråk)
        every { kontantstøtteSøknad.dokumentasjon } returns
            listOf(
                dokumentasjon,
            )

        val versjonertKontantstøtteSøknad = VersjonertKontantstøtteSøknadV6(kontantstøtteSøknad = kontantstøtteSøknad)

        // Bokmål
        var kontantstøtteMapForSpråk =
            søknadSpråkvelgerService.konverterKontantstøtteSøknadTilMapForSpråk(versjonertKontantstøtteSøknad, "nb")
        var kontantstøtteSøknadJsonNode: JsonNode = jsonMapper.valueToTree(kontantstøtteMapForSpråk)

        // Tester TekstPåSpråkMapSerializer
        assertEquals("Norge", kontantstøtteSøknadJsonNode["teksterTilPdf"]["testApiNavn"].asString())
        assertEquals("Norge", kontantstøtteSøknadJsonNode["barn"][0]["teksterTilPdf"]["testApiNavn"].asString())
        assertEquals(
            "Norge",
            kontantstøtteSøknadJsonNode["dokumentasjon"][0]["dokumentasjonSpråkTittel"].asString(),
        )

        // Tester SøknadsfeltSerializer
        assertEquals("Norge", kontantstøtteSøknadJsonNode["erBarnAdoptert"]["label"].asString())
        assertEquals("Norge", kontantstøtteSøknadJsonNode["erBarnAdoptert"]["verdi"].asString())

        // Nynorsk

        val kontantstøtteMapForSpråkNN =
            søknadSpråkvelgerService.konverterKontantstøtteSøknadTilMapForSpråk(versjonertKontantstøtteSøknad, "nn")
        kontantstøtteSøknadJsonNode = jsonMapper.valueToTree(kontantstøtteMapForSpråkNN)

        // Tester TekstPåSpråkMapSerializer
        assertEquals("Noreg", kontantstøtteSøknadJsonNode["teksterTilPdf"]["testApiNavn"].asString())
        assertEquals("Noreg", kontantstøtteSøknadJsonNode["barn"][0]["teksterTilPdf"]["testApiNavn"].asString())
        assertEquals(
            "Noreg",
            kontantstøtteSøknadJsonNode["dokumentasjon"][0]["dokumentasjonSpråkTittel"].asString(),
        )

        // Tester SøknadsfeltSerializer
        assertEquals("Noreg", kontantstøtteSøknadJsonNode["erBarnAdoptert"]["label"].asString())
        assertEquals("Noreg", kontantstøtteSøknadJsonNode["erBarnAdoptert"]["verdi"].asString())

        // Engelsk
        kontantstøtteMapForSpråk =
            søknadSpråkvelgerService.konverterKontantstøtteSøknadTilMapForSpråk(versjonertKontantstøtteSøknad, "en")
        kontantstøtteSøknadJsonNode = jsonMapper.valueToTree(kontantstøtteMapForSpråk)

        // Tester TekstPåSpråkMapSerializer
        assertEquals("Norway", kontantstøtteSøknadJsonNode["teksterTilPdf"]["testApiNavn"].asString())
        assertEquals("Norway", kontantstøtteSøknadJsonNode["barn"][0]["teksterTilPdf"]["testApiNavn"].asString())
        assertEquals(
            "Norway",
            kontantstøtteSøknadJsonNode["dokumentasjon"][0]["dokumentasjonSpråkTittel"].asString(),
        )

        // Tester SøknadsfeltSerializer
        assertEquals("Norway", kontantstøtteSøknadJsonNode["erBarnAdoptert"]["label"].asString())
        assertEquals("Norway", kontantstøtteSøknadJsonNode["erBarnAdoptert"]["verdi"].asString())
    }
}
