package no.nav.familie.ba.mottak.søknad

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SøknadTreeWalkerTest {

    val søknad = SøknadTestData.søknad()
    val mapSøknadsfelter = SøknadTreeWalker.mapSøknadsfelter(søknad)
    val verdiliste = mapSøknadsfelter["verdiliste"] as List<Map<String, Any?>>

    @Test
    fun `mapSøknadsfelter returnerer en map-struktur med feltene fra søknaden`() {
        assertTrue(mapSøknadsfelter.isNotEmpty())
        assertEquals("Søknad barnetrygd - 33-00.07", mapSøknadsfelter["label"])
        assertEquals(3, verdiliste.size) // tre verdilister: søker, barn1 og barn2
    }

    @Test
    fun `Test at alle labels har en tilhørende verdi (alle verdiers klasse i endNodes)`() {
        assertTrue(
                verdiliste.none { it.containsKey("verdiliste") && (it["verdiliste"] as List<*>).isEmpty() }
        )
    }

    @Test
    fun `Test at verdier bevares og holdes sammen`() {
        val barneliste = verdiliste[2]["verdiliste"] as List<Map<String, Any?>>
        val navn = barneliste.filter { it["label"] == "Barnets fulle navn" }.map { it["verdi"] }.toList()
        val alder = barneliste.filter { it["label"] == "alder" }.map { it["verdi"] }.toList()
        assertTrue(
                (navn[0] == "barn1" && alder[0] == "4 år")
                        && (navn[1] == "barn2" && alder[1] == "1 år")
        )

    }
}