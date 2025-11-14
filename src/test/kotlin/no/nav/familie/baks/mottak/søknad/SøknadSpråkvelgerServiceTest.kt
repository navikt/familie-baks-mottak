package no.nav.familie.baks.mottak.søknad

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.søknad.Søknadsfelt
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV6
import no.nav.familie.kontrakter.ks.søknad.v1.Søknaddokumentasjon
import no.nav.familie.kontrakter.ks.søknad.v1.TekstPåSpråkMap
import no.nav.familie.kontrakter.ks.søknad.v6.Barn
import no.nav.familie.kontrakter.ks.søknad.v6.KontantstøtteSøknad
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class SøknadSpråkvelgerServiceTest {
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService = SøknadSpråkvelgerService()

    @Test
    fun `konverterKontantstøtteSøknadTilMapForSpråk - skal konvertere KontantstøtteSøknad til et Map med tekster på spesifisert språk`() {
        val kontantstøtteSøknad: KontantstøtteSøknad = mockk()
        val barn: Barn = mockk()
        val dokumentasjon: Søknaddokumentasjon = mockk()
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
        var kontantstøtteSøknadJsonNode: JsonNode = objectMapper.valueToTree(kontantstøtteMapForSpråk)

        // Tester TekstPåSpråkMapSerializer
        assertEquals("Norge", kontantstøtteSøknadJsonNode["teksterTilPdf"]["testApiNavn"].textValue())
        assertEquals("Norge", kontantstøtteSøknadJsonNode["barn"][0]["teksterTilPdf"]["testApiNavn"].textValue())
        assertEquals(
            "Norge",
            kontantstøtteSøknadJsonNode["dokumentasjon"][0]["dokumentasjonSpråkTittel"].textValue(),
        )

        // Tester SøknadsfeltSerializer
        assertEquals("Norge", kontantstøtteSøknadJsonNode["erBarnAdoptert"]["label"].textValue())
        assertEquals("Norge", kontantstøtteSøknadJsonNode["erBarnAdoptert"]["verdi"].textValue())

        // Nynorsk
        kontantstøtteMapForSpråk =
            søknadSpråkvelgerService.konverterKontantstøtteSøknadTilMapForSpråk(versjonertKontantstøtteSøknad, "nn")
        kontantstøtteSøknadJsonNode = objectMapper.valueToTree(kontantstøtteMapForSpråk)

        // Tester TekstPåSpråkMapSerializer
        assertEquals("Noreg", kontantstøtteSøknadJsonNode["teksterTilPdf"]["testApiNavn"].textValue())
        assertEquals("Noreg", kontantstøtteSøknadJsonNode["barn"][0]["teksterTilPdf"]["testApiNavn"].textValue())
        assertEquals(
            "Noreg",
            kontantstøtteSøknadJsonNode["dokumentasjon"][0]["dokumentasjonSpråkTittel"].textValue(),
        )

        // Tester SøknadsfeltSerializer
        assertEquals("Noreg", kontantstøtteSøknadJsonNode["erBarnAdoptert"]["label"].textValue())
        assertEquals("Noreg", kontantstøtteSøknadJsonNode["erBarnAdoptert"]["verdi"].textValue())

        // Engelsk
        kontantstøtteMapForSpråk =
            søknadSpråkvelgerService.konverterKontantstøtteSøknadTilMapForSpråk(versjonertKontantstøtteSøknad, "en")
        kontantstøtteSøknadJsonNode = objectMapper.valueToTree(kontantstøtteMapForSpråk)

        // Tester TekstPåSpråkMapSerializer
        assertEquals("Norway", kontantstøtteSøknadJsonNode["teksterTilPdf"]["testApiNavn"].textValue())
        assertEquals("Norway", kontantstøtteSøknadJsonNode["barn"][0]["teksterTilPdf"]["testApiNavn"].textValue())
        assertEquals(
            "Norway",
            kontantstøtteSøknadJsonNode["dokumentasjon"][0]["dokumentasjonSpråkTittel"].textValue(),
        )

        // Tester SøknadsfeltSerializer
        assertEquals("Norway", kontantstøtteSøknadJsonNode["erBarnAdoptert"]["label"].textValue())
        assertEquals("Norway", kontantstøtteSøknadJsonNode["erBarnAdoptert"]["verdi"].textValue())
    }
}
